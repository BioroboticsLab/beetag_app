package com.aki.beetag;

import android.content.Context;
import android.graphics.Camera;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.app.Activity;
import android.hardware.camera2.CameraManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class CameraActivity extends Activity {

    // TextureView that holds the camera's preview image
    private TextureView cameraPreview;
    // listener for the camera's preview image
    private TextureView.SurfaceTextureListener cameraPreviewListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            setupCamera(width, height);
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

    // compares two (screen) dimensions by area
    private class AreaComparator implements Comparator<Size> {
        @Override
        public int compare(Size o1, Size o2) {
            return (o1.getHeight() * o1.getWidth()) - (o2.getHeight() * o2.getWidth());
        }
    }

    // returns the smallest (by area) Size whose width and height are at least
    // equal to 'matchWidth' and 'matchHeight'; returns the first element of
    // 'options' if no such Size is found
    private Size bestMatchingSize(Size[] options, int matchWidth, int matchHeight) {
        ArrayList<Size> compatible = new ArrayList<Size>();
        for (Size candidate : options) {
            // make sure that aspect ratios match...
            if ((candidate.getWidth() / candidate.getHeight() == matchWidth / matchHeight) &&
                    // and that dimensions are large enough
                    (candidate.getWidth() >= matchWidth) &&
                    (candidate.getHeight() >= matchHeight)) {
                compatible.add(candidate);
            }
        }
        if (compatible.isEmpty()) {
            return options[0];
        } else {
            return Collections.min(compatible, new AreaComparator());
        }
    }

    private CameraDevice camera;
    private String cameraId;
    // listener for the camera
    private CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            camera = cameraDevice;
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
                previewSize = bestMatchingSize(outputSizes, width, height);
                cameraId = id;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // release the camera ressource
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
