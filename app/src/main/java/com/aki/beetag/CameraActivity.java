package com.aki.beetag;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.app.Activity;
import android.hardware.camera2.CameraManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class CameraActivity extends Activity {

    private static final int REQUEST_PERMISSION_CAMERA = 0;
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
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }
    // returns true if the camera orientation sensor and device orientation sensor display values
    // that are orthogonal to each other, false otherwise
    private boolean previewNeedsRotation(CameraCharacteristics cameraCharacteristics, int deviceRotation) {
        int cameraOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int deviceOrientation = ORIENTATIONS.get(deviceRotation);
        int relativeOrientation = cameraOrientation + deviceOrientation;
        return (relativeOrientation % 180 == 90);
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
    // if no large enough Size is found, returns the largest one
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

    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // assign the camera preview TextureView
        cameraPreview = (TextureView) findViewById(R.id.textureview_camera_preview);
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
        if (requestCode == REQUEST_PERMISSION_CAMERA) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "This application needs access to the camera to function properly.", Toast.LENGTH_SHORT).show();
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
                for (Size s : outputSizes) {
                    Log.d("cameradebug", "width: " + s.getWidth() + ", height: " + s.getHeight());
                }
                Log.d("cameradebug", "-----");
                int deviceRotation = getWindowManager().getDefaultDisplay().getRotation();
                if (previewNeedsRotation(cameraCharacteristics, deviceRotation)) {
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
            camera.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            try {
                                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(getApplicationContext(), "Oops! Something went wrong while trying to use the camera :(", Toast.LENGTH_SHORT);
                        }
                    }, null);
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
}
