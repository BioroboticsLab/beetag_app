package com.aki.beetag;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.app.Activity;
import android.hardware.camera2.CameraManager;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class CameraActivity extends Activity {

    private static final int REQUEST_PERMISSION_CAMERA = 0;
    private static final int REQUEST_PERMISSION_EXTERNAL_STORAGE = 1;
    // TextureView that holds the camera's preview image
    private TextureView cameraPreview;
    // listener for the camera's preview image
    private TextureView.SurfaceTextureListener cameraPreviewListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            setupCamera(width, height);
            activateCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };
    private Size previewSize;

    private Size imageSize;
    private ImageReader imageReader;
    // listener for if the captured image is available
    private final ImageReader.OnImageAvailableListener onImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imgReader) {
                    backgroundHandler.post(new ImageSaver(imgReader.acquireLatestImage()));
                }
            };

    private class ImageSaver implements Runnable {

        private final Image image;

        public ImageSaver(Image image) {
            this.image = image;
        }

        @Override
        public void run() {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(imageFilePath);
                fileOutputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                image.close();
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private int totalSensorOrientation;

    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    // returns the final orientation of the sensor in degrees, consisting of the sensor orientation
    // and the device orientation added together; can be one of 0, 90, 180 and 270
    private int totalSensorRotation(CameraCharacteristics cameraCharacteristics, int deviceRotation) {
        int cameraOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int deviceOrientation = ORIENTATIONS.get(deviceRotation);
        return ((cameraOrientation + deviceOrientation) % 360);
    }

    // compares two (screen) dimensions by area
    private class AreaComparator implements Comparator<Size> {
        @Override
        public int compare(Size o1, Size o2) {
            return (o1.getHeight() * o1.getWidth()) - (o2.getHeight() * o2.getWidth());
        }
    }

    // returns the smallest (by area) Size whose width and height are at least
    // equal to 'matchWidth' and 'matchHeight',
    // prioritizes Size that has matching aspect ratio;
    // if no large enough Size is found, returns the largest one (by area)
    private Size getOptimalSize(Size[] options, int matchWidth, int matchHeight) {
        ArrayList<Size> correctRatioLarge = new ArrayList<Size>();
        ArrayList<Size> correctRatioSmall = new ArrayList<Size>();
        ArrayList<Size> incorrectRatioLarge = new ArrayList<Size>();
        ArrayList<Size> incorrectRatioSmall = new ArrayList<Size>();
        for (Size candidate : options) {
            // make sure that aspect ratios match
            double candidateAspectRatio = (double) candidate.getWidth() / (double) candidate.getHeight();
            double matchAspectRatio = (double) matchWidth / (double) matchHeight;
            if (candidateAspectRatio == matchAspectRatio) {
                if (candidate.getWidth() >= matchWidth && candidate.getHeight() >= matchHeight) {
                    correctRatioLarge.add(candidate);
                } else {
                    correctRatioSmall.add(candidate);
                }
            } else {
                if (candidate.getWidth() >= matchWidth && candidate.getHeight() >= matchHeight) {
                    incorrectRatioLarge.add(candidate);
                } else {
                    incorrectRatioSmall.add(candidate);
                }
            }
        }
        AreaComparator areaComparator = new AreaComparator();
        if (correctRatioLarge.isEmpty()) {
            if (incorrectRatioLarge.isEmpty()) {
                Size correctRatioMax = Collections.max(correctRatioSmall, areaComparator);
                Size incorrectRatioMax = Collections.max(incorrectRatioSmall, areaComparator);
                return (areaComparator.compare(incorrectRatioMax, correctRatioMax) > 0) ?
                        incorrectRatioMax : correctRatioMax;
            } else {
                return Collections.min(incorrectRatioLarge, areaComparator);
            }
        } else {
            return Collections.min(correctRatioLarge, areaComparator);
        }
    }

    private CameraDevice camera;
    private String cameraId;
    // listener for the camera
    private CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            camera = cameraDevice;
            startCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            camera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {

        }
    };
    private CaptureRequest.Builder captureRequestBuilder;

    private CameraCaptureSession cameraCaptureSession;
    private CameraCaptureSession.CaptureCallback cameraCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {
            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
            if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                    afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                Toast.makeText(getApplicationContext(), "AF locked!", Toast.LENGTH_SHORT).show();
                startCaptureRequest();
            } else {
                Toast.makeText(getApplicationContext(), "AF not locked!", Toast.LENGTH_SHORT).show();
            }
        }
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }
    };

    private ImageButton cameraShutterButton;

    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        createImageFolder();

        // assign the camera preview TextureView
        cameraPreview = (TextureView) findViewById(R.id.textureview_camera_preview);
        cameraShutterButton = (ImageButton) findViewById(R.id.button_camera_shutter);
        cameraShutterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkStorageWritePermission();
                camera_autofocus();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();

        // check if the camera preview TextureView is available or not
        if (cameraPreview.isAvailable()) {
            setupCamera(cameraPreview.getWidth(), cameraPreview.getHeight());
            activateCamera();
        }
        else {
            // TextureView is not available, set up listener
            cameraPreview.setSurfaceTextureListener(cameraPreviewListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
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
            case REQUEST_PERMISSION_CAMERA:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "This application needs access to the camera to function properly.", Toast.LENGTH_SHORT).show();
                }
                break;
            case REQUEST_PERMISSION_EXTERNAL_STORAGE:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "This application needs access to the external storage to function properly.", Toast.LENGTH_SHORT).show();
                } else {
                    try {
                        Toast.makeText(getApplicationContext(), "External storage permission successfully granted.", Toast.LENGTH_SHORT).show();
                        createImageFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
        }
    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(id);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap streamConfigurations = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] outputSizes = streamConfigurations.getOutputSizes(SurfaceTexture.class);
                int deviceRotation = getWindowManager().getDefaultDisplay().getRotation();
                totalSensorOrientation = totalSensorRotation(cameraCharacteristics, deviceRotation);
                if (totalSensorOrientation == 90 || totalSensorOrientation == 270) {
                    // swap width and height to match the screen rotation
                    int widthBuf = width;
                    width = height;
                    height = widthBuf;
                }
                previewSize = getOptimalSize(outputSizes, width, height);
                // transform the TextureView to keep correct aspect ratio
                Matrix transform = new Matrix();
                cameraPreview.getTransform(transform);
                transform.setScale((float) width / (float) previewSize.getWidth(), (float) height / (float) previewSize.getHeight());
                cameraPreview.setTransform(transform);

                imageSize = Collections.max(new ArrayList<Size>(Arrays.asList(
                        streamConfigurations.getOutputSizes(ImageFormat.JPEG))), new AreaComparator());
                imageReader = ImageReader.newInstance(imageSize.getWidth(), imageSize.getHeight(), ImageFormat.JPEG, 1);
                imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler);

                cameraId = id;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void activateCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler);
            } else {
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, REQUEST_PERMISSION_CAMERA);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startCameraPreview() {
        SurfaceTexture surfaceTexture = cameraPreview.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);
            camera.createCaptureSession(Arrays.asList(previewSurface, imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                            try {
                                cameraCaptureSession = captureSession;
                                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(getApplicationContext(), "Oops! Something went wrong while trying to use the camera :(", Toast.LENGTH_SHORT).show();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startCaptureRequest() {
        try {
            captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, totalSensorOrientation);

            CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);

                    try {
                        createImageFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };

            cameraCaptureSession.capture(captureRequestBuilder.build(), captureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // release the camera resource
    private void closeCamera() {
        if (camera != null) {
            camera.close();
            camera = null;
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

    private File imageFolder;
    private String imageFilePath;

    private void createImageFolder() {
        File publicPictureFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        imageFolder = new File(publicPictureFolder, "Beetags");
        if (!imageFolder.exists()) {
            imageFolder.mkdirs();
        }
    }

    private File createImageFile() throws IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        File imageFile = File.createTempFile(timestamp, ".jpg", imageFolder);
        imageFilePath = imageFile.getAbsolutePath();
        return imageFile;
    }

    private void checkStorageWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED) {
                try {
                    createImageFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, "Application needs access to external storage to store the image files.",
                            Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_EXTERNAL_STORAGE);
            }
        } else {
            try {
                createImageFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void camera_autofocus() {
        captureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            cameraCaptureSession.capture(captureRequestBuilder.build(), cameraCaptureCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
