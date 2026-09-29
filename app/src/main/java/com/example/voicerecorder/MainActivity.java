package com.example.voicerecorder;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQUEST_RECORD_AUDIO = 100;
    private MediaRecorder recorder;
    private File tempFile;
    private TextView statusText;
    private FirebaseRemoteConfig remoteConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("🎙 Voice Recorder");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);

        statusText = new TextView(this);
        statusText.setText("Ready");
        statusText.setTextSize(18);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 30, 0, 30);

        Button startButton = new Button(this);
        startButton.setText("🎙 Start Recording");

        Button stopButton = new Button(this);
        stopButton.setText("⏹ Stop");
        stopButton.setEnabled(false);

        layout.addView(title);
        layout.addView(statusText);
        layout.addView(startButton);
        layout.addView(stopButton);

        setContentView(layout);

        startButton.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_RECORD_AUDIO
                );
            } else {
                startRecording();
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
            }
        });

        stopButton.setOnClickListener(v -> {
            stopRecording();
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        });

        setupRemoteConfig();
    }

    private void setupRemoteConfig() {
        remoteConfig = FirebaseRemoteConfig.getInstance();

        FirebaseRemoteConfigSettings settings =
                new FirebaseRemoteConfigSettings.Builder()
                        .setMinimumFetchIntervalInSeconds(3600)
                        .build();

        remoteConfig.setConfigSettingsAsync(settings);

        remoteConfig.setDefaultsAsync(new java.util.HashMap<String, Object>() {{
            put("latest_version_code", 1L);
            put("latest_version_name", "1.0");
            put("update_url", "");
        }});

        remoteConfig.fetchAndActivate()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        checkForUpdate();
                    }
                });
    }

    private void checkForUpdate() {
        long latestVersionCode =
                remoteConfig.getLong("latest_version_code");

        String latestVersionName =
                remoteConfig.getString("latest_version_name");

        String updateUrl =
                remoteConfig.getString("update_url");

        try {
            long currentVersionCode =
                    getPackageManager()
                            .getPackageInfo(getPackageName(), 0)
                            .versionCode;

            if (latestVersionCode > currentVersionCode
                    && updateUrl != null
                    && !updateUrl.trim().isEmpty()) {

                new android.app.AlertDialog.Builder(this)
                        .setTitle("New Version Available")
                        .setMessage(
                                "Version " + latestVersionName +
                                " is available."
                        )
                        .setPositiveButton("Update", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(updateUrl)
                                );
                                startActivity(intent);
                            } catch (Exception e) {
                                Toast.makeText(
                                        this,
                                        "Unable to open update link",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        })
                        .setNegativeButton("Later", null)
                        .show();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startRecording() {
        try {
            File musicDir = getExternalFilesDir(
                    Environment.DIRECTORY_MUSIC
            );

            if (musicDir == null) {
                statusText.setText("Storage unavailable");
                return;
            }

            String timestamp = new SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.getDefault()
            ).format(new Date());

            tempFile = new File(
                    musicDir,
                    "Recording_" + timestamp + ".m4a"
            );

            recorder = new MediaRecorder();

            recorder.setAudioSource(
                    MediaRecorder.AudioSource.MIC
            );

            recorder.setOutputFormat(
                    MediaRecorder.OutputFormat.MPEG_4
            );

            recorder.setAudioEncoder(
                    MediaRecorder.AudioEncoder.AAC
            );

            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setOutputFile(tempFile.getAbsolutePath());

            recorder.prepare();
            recorder.start();

            statusText.setText("🔴 Recording...");
        } catch (Exception e) {
            e.printStackTrace();
            statusText.setText("Unable to start recording");
        }
    }

    private void stopRecording() {
        if (recorder == null) {
            return;
        }

        try {
            recorder.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }

        recorder.release();
        recorder = null;

        if (tempFile != null && tempFile.exists()) {
            saveToDownloads(tempFile);
        }

        statusText.setText("Recording saved to Download/VoiceRecorder");
    }

    private void saveToDownloads(File sourceFile) {
        String fileName = sourceFile.getName();

        ContentValues values = new ContentValues();
        values.put(
                MediaStore.Downloads.DISPLAY_NAME,
                fileName
        );
        values.put(
                MediaStore.Downloads.MIME_TYPE,
                "audio/mp4"
        );
        values.put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS
                        + "/VoiceRecorder"
        );

        Uri uri = getContentResolver().insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
        );

        if (uri == null) {
            Toast.makeText(
                    this,
                    "Unable to save recording",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        try (
                FileInputStream input =
                        new FileInputStream(sourceFile);
                FileOutputStream output =
                        (FileOutputStream)
                                getContentResolver()
                                        .openOutputStream(uri)
        ) {
            byte[] buffer = new byte[8192];
            int length;

            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }

            output.flush();

            sourceFile.delete();

            Toast.makeText(
                    this,
                    "Saved to Download/VoiceRecorder",
                    Toast.LENGTH_LONG
            ).show();

        } catch (IOException e) {
            e.printStackTrace();

            getContentResolver().delete(uri, null, null);

            Toast.makeText(
                    this,
                    "Unable to save recording",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (Exception ignored) {
            }

            recorder.release();
            recorder = null;
        }

        super.onDestroy();
    }
}
