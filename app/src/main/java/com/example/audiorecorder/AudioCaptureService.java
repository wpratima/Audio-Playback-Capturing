package com.example.audiorecorder;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat.Builder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

public final class AudioCaptureService extends Service {

    private static final int NUM_SAMPLES_PER_READ = 1024;
    private static final int BYTES_PER_SAMPLE = 2; // 2 bytes since we hardcoded the PCM 16-bit format
    private static final int BUFFER_SIZE_IN_BYTES = NUM_SAMPLES_PER_READ * BYTES_PER_SAMPLE;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private Thread audioCaptureThread;
    private AudioRecord audioRecord;
    public static final String ACTION_START = "AudioCaptureService:Start";
    public static final String ACTION_STOP = "AudioCaptureService:Stop";
    public static final String EXTRA_RESULT_DATA = "AudioCaptureService:Extra:ResultData";

    @Override
    public void onCreate() {
        super.onCreate();
        this.createNotificationChannel();
        this.startForeground(123, (new Builder((Context) this, "AudioCapture channel")).build());
        mediaProjectionManager = (MediaProjectionManager) this.getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);

    }

    private final void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel("AudioCapture channel", (CharSequence) "Audio Capture Service Channel", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = this.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int action = START_NOT_STICKY;
        if (intent != null) {
            switch (intent.getAction()) {
                case ACTION_STOP:
                    this.stopAudioCapture();
                    action = START_NOT_STICKY;
                    break;
                case ACTION_START:
                    mediaProjection = mediaProjectionManager.getMediaProjection(
                            Activity.RESULT_OK,
                            intent.getParcelableExtra(EXTRA_RESULT_DATA)
                    );
                    this.startAudioCapture();
                    action = START_STICKY;
                    break;
                default:
                    try {
                        throw (Throwable) (new IllegalArgumentException("Unexpected action received: " + intent.getAction()));
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
            }
        } else {
            action = START_NOT_STICKY;
        }
        return action;
    }

    private final void startAudioCapture() {

        AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build();
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(8000)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
         audioRecord = new AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                // For optimal performance, the buffer size
                // can be optionally specified to store audio samples.
                // If the value is not specified,
                // uses a single frame and lets the
                // native code figure out the minimum buffer size.
                .setBufferSizeInBytes(AudioCaptureService.BUFFER_SIZE_IN_BYTES)
                .setAudioPlaybackCaptureConfig(config)
                .build();

        audioRecord.startRecording();
        audioCaptureThread = new Thread() {
            @Override
            public void run() {
                    ///sleep(30);
                    File outputFile = AudioCaptureService.this.createAudioFile();
                    Log.d("AudioCaptureService", "Created file for capture target: " + outputFile.getAbsolutePath());
                    writeAudioToFile(outputFile);
            }
        };

        audioCaptureThread.start();
    }

    private final File createAudioFile() {
        File audioCapturesDirectory = new File(this.getExternalFilesDir((String) null), "/AudioCaptures");
        if (!audioCapturesDirectory.exists()) {
            audioCapturesDirectory.mkdirs();
        }

        String timestamp = (new SimpleDateFormat("dd-MM-yyyy-hh-mm-ss", Locale.US)).format(new Date());
        String fileName = "Capture-" + timestamp + ".pcm";
        return new File(audioCapturesDirectory.getAbsolutePath() + "/" + fileName);
    }

    private final void writeAudioToFile(File outputFile) {
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(outputFile);
            short[] capturedAudioSamples = new short[NUM_SAMPLES_PER_READ];
            while (!audioCaptureThread.isInterrupted()) {
                if(audioRecord != null){
                    Log.d("writeAudioToFile", String.valueOf(capturedAudioSamples.length));
                    audioRecord.read(capturedAudioSamples, 0, NUM_SAMPLES_PER_READ);
                    fileOutputStream.write(this.toByteArray(capturedAudioSamples), 0, BUFFER_SIZE_IN_BYTES);
                }else{
                    Log.d("writeAudioToFile", "audio record is null");
                }
            }
            fileOutputStream.close();
            Log.d("AudioCaptureService", "Audio capture finished for " + outputFile.getAbsolutePath() + ". File size is " + outputFile.length() + " bytes.");
            return;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final void stopAudioCapture() {
        if (mediaProjection == null) {
            String var1 = "Tried to stop audio capture, but there was no ongoing capture in place!";
            try {
                throw (Throwable) (new IllegalArgumentException(var1.toString()));
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        } else {
            audioCaptureThread.interrupt();
            try {
                audioCaptureThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;

            mediaProjection.stop();
            stopSelf();
        }
    }

    @Nullable
    public IBinder onBind(@Nullable Intent p0) {
        return null;
    }

    private final byte[] toByteArray(short[] $this$toByteArray) {
        byte[] bytes = new byte[$this$toByteArray.length * 2];
        int i = 0;

        for (int var4 = $this$toByteArray.length; i < var4; ++i) {
            int var10001 = i * 2;
            short var5 = $this$toByteArray[i];
            short var6 = 255;
            boolean var7 = false;
            bytes[var10001] = (byte) ((short) (var5 & var6));
            bytes[i * 2 + 1] = (byte) ($this$toByteArray[i] >> 8);
            $this$toByteArray[i] = 0;
        }

        return bytes;
    }
}
