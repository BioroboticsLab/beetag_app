package com.aki.beetag;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.view.View;
import android.app.Activity;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.flurgle.camerakit.CameraKit;
import com.flurgle.camerakit.CameraListener;
import com.flurgle.camerakit.CameraView;

public class CameraActivity extends Activity {

    private static final int REQUEST_PERMISSION_EXTERNAL_STORAGE = 1;
    private static final int REQUEST_CAPTURE_IMAGE = 2;

    private ImageButton cameraShutterButton;

    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;

    private boolean storageWritePermissionGranted;

    private CameraView cameraView;

    private File imageFolder;
    private File imageFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkStorageWritePermission();

        setContentView(R.layout.activity_camera);

        createImageFolder();

        cameraShutterButton = findViewById(R.id.button_camera_shutter);
        cameraShutterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /*
                if (capturingImage || !storageWritePermissionGranted) {
                    return;
                }
                capturingImage = true;
                try {
                    createImageFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                cameraView.captureImage();
                */
                dispatchCaptureIntent();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CAPTURE_IMAGE:
                // rename image file so that timestamp is correct
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, "RESULT_OK", Toast.LENGTH_SHORT).show();
                    try {
                        if (!imageFile.renameTo(createImageFile())) {
                            Toast.makeText(this, "Renaming failed, timestamp of image in 'Beetags' folder may be wrong.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(this, "RESULT_CANCELED", Toast.LENGTH_SHORT).show();
                    if (!imageFile.delete()) {
                        Toast.makeText(this, "Deletetion failed, there may be empty image files in 'Beetags' folder.", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }
    }

    private void dispatchCaptureIntent() {
        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (captureIntent.resolveActivity(getPackageManager()) != null) {
            imageFile = null;
            try {
                imageFile = createImageFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (imageFile != null) {
                String authorities = getApplicationContext().getPackageName() + ".fileprovider";
                Uri imageUri = FileProvider.getUriForFile(this, authorities, imageFile);
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                startActivityForResult(captureIntent, REQUEST_CAPTURE_IMAGE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        if (hasFocus) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSION_EXTERNAL_STORAGE:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "This application needs access to the external storage to function properly.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), "External storage permission successfully granted.", Toast.LENGTH_SHORT).show();
                    storageWritePermissionGranted = true;
                }
                break;
        }
    }

    private void startBackgroundThread() {
        backgroundHandlerThread = new HandlerThread("BackgroundThread");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundHandlerThread.quitSafely();
        try {
            backgroundHandlerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        backgroundHandlerThread = null;
        backgroundHandler = null;
    }

    private void createImageFolder() {
        File publicPictureFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        imageFolder = new File(publicPictureFolder, "Beetags");
        if (!imageFolder.exists()) {
            imageFolder.mkdirs();
        }
    }

    private File createImageFile() throws IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        File imgFile = File.createTempFile(timestamp + "_", ".jpg", imageFolder);
        return imgFile;
    }

    private void checkStorageWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED) {
                storageWritePermissionGranted = true;
            } else {
                storageWritePermissionGranted = false;
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, "Application needs access to external storage to store the image files.",
                            Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_EXTERNAL_STORAGE);
            }
        } else {
            // in this case we can just pretend as if permission was granted
            storageWritePermissionGranted = true;
        }
    }
}
