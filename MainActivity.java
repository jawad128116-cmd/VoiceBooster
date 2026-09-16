package com.example.girlvoicechanger;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};

    private boolean isBoosting = false;
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private Thread boostThread;

    private float volumeMultiplier = 1.0f;
    private TextView tvStatus, tvLiveGain;
    private SeekBar seekBarVolume;
    private Button btnToggle;
    private AudioManager audioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvLiveGain = findViewById(R.id.tvLiveGain);
        seekBarVolume = findViewById(R.id.seekBarVolume);
        btnToggle = findViewById(R.id.btnToggle);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Request Mic Permissions
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        seekBarVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Progress 0 to 49 translates to multiplier 1.0f to 50.0f (Super Loud)
                volumeMultiplier = 1.0f + (progress * 1.0f);
                tvLiveGain.setText("Boost Level: " + String.format("%.1f", volumeMultiplier) + "x 🔊");
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnToggle.setOnClickListener(v -> {
            if (!isBoosting) {
                startBoosting();
            } else {
                stopBoosting();
            }
        });
    }

    private void startBoosting() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }

        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(true);

        int sampleRate = 44100;
        int channelConfigIn = AudioFormat.CHANNEL_IN_MONO;
        int channelConfigOut = AudioFormat.CHANNEL_OUT_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = 44100;
        }

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, channelConfigIn, audioFormat, bufferSize);
            audioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL, sampleRate, channelConfigOut, audioFormat, bufferSize, AudioTrack.MODE_STREAM);

            audioRecord.startRecording();
            audioTrack.play();

            isBoosting = true;
            tvStatus.setText("Status: Boosting Active 🟢");
            btnToggle.setText("STOP ULTRA BOOSTER");

            boostThread = new Thread(() -> {
                short[] buffer = new short[bufferSize / 2];
                while (isBoosting) {
                    int readSize = audioRecord.read(buffer, 0, buffer.length);
                    if (readSize > 0) {
                        for (int i = 0; i < readSize; i++) {
                            // High gain amplification with safety clipping limit
                            float amplified = buffer[i] * volumeMultiplier;
                            if (amplified > 32767.0f) {
                                buffer[i] = 32767; // Hard clip to protect hardware & prevent screeching crash
                            } else if (amplified < -32768.0f) {
                                buffer[i] = -32768;
                            } else {
                                buffer[i] = (short) amplified;
                            }
                        }
                        audioTrack.write(buffer, 0, readSize);
                    }
                }
            });
            boostThread.start();

        } catch (SecurityException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void stopBoosting() {
        isBoosting = false;
        try {
            if (boostThread != null) {
                boostThread.join();
            }
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
            if (audioTrack != null) {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        tvStatus.setText("Status: Stopped 🔴");
        btnToggle.setText("START ULTRA BOOSTER");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
        if (!permissionToRecordAccepted) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBoosting();
    }
}
