package com.example.audiorecorder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private MediaProjectionManager mediaProjectionManager;
    private Button startBtn;
    private Button stopBtn;
    private int RECORD_AUDIO_PERMISSION_REQUEST_CODE = 42;
    private int MEDIA_PROJECTION_REQUEST_CODE = 13;
    Timer audioTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startBtn = findViewById(R.id.btn_start_recording);
        stopBtn = findViewById(R.id.btn_stop_recording);

        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCapturing();
            }
        });

        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopCapturing();
            }
        });

        //intiAudioRecorder();

    }

    public void intiAudioRecorder(){
        audioTimer = new Timer();
        audioTimer.scheduleAtFixedRate(
                new TimerTask() {
                    @Override
                    public void run() {
                        stopCapturing();
                        startCapturing();
                    }
                }, 300000,
                300000);
    }

    private void setButtonsEnabled(Boolean isCapturingAudio) {
        startBtn.setEnabled(!isCapturingAudio);
        stopBtn.setEnabled(isCapturingAudio);
    }

    private void startCapturing() {
        if (!isRecordAudioPermissionGranted()) {
            requestRecordAudioPermission();
        } else {
            startMediaProjectionRequest();
        }
    }

    private void stopCapturing() {
        setButtonsEnabled(false);
        Intent audioCaptureIntent = new Intent(this, AudioCaptureService.class);
        audioCaptureIntent.setAction(AudioCaptureService.ACTION_STOP);
        startService(audioCaptureIntent);
    }

    private Boolean isRecordAudioPermissionGranted(){
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRecordAudioPermission() {
        ArrayList<String> wanted = new ArrayList<>();
            wanted.add(Manifest.permission.RECORD_AUDIO);
            //wanted.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            //wanted.add(Manifest.permission.READ_EXTERNAL_STORAGE);

        requestPermissions(wanted.toArray(new String[wanted.size()]), RECORD_AUDIO_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        this,
                        "Permissions to capture audio granted. Click the button once again.",
                        Toast.LENGTH_SHORT
                ).show();
            } else {
                Toast.makeText(
                        this, "Permissions to capture audio denied.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    /**
     * Before a capture session can be started, the capturing app must
     * call MediaProjectionManager.createScreenCaptureIntent().
     * This will display a dialog to the user, who must tap "Start now" in order for a
     * capturing session to be started. This will allow both video and audio to be captured.
     */
    private void startMediaProjectionRequest() {
        // use applicationContext to avoid memory leak on Android 10.
        // see: https://partnerissuetracker.corp.google.com/issues/139732252
        mediaProjectionManager = (MediaProjectionManager) getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                mediaProjectionManager.createScreenCaptureIntent(),
                MEDIA_PROJECTION_REQUEST_CODE
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(
                        this,
                        "MediaProjection permission obtained. Foreground service will be started to capture audio.",
                        Toast.LENGTH_SHORT
                ).show();

                Intent audioCaptureIntent = new Intent(this, AudioCaptureService.class);
                audioCaptureIntent.setAction(AudioCaptureService.ACTION_START);
                audioCaptureIntent.putExtra(AudioCaptureService.EXTRA_RESULT_DATA, data);
                startForegroundService(audioCaptureIntent);
                setButtonsEnabled(true);
            } else {
                Toast.makeText(
                        this, "Request to obtain MediaProjection denied.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }
}
