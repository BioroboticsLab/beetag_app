package com.aki.beetag;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import static com.bumptech.glide.request.RequestOptions.centerCropTransform;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;


public class GalleryActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 2;
    private static final int REQUEST_CAPTURE_IMAGE = 3;

    private FloatingActionButton cameraButton;
    private GridView imageGridView;

    private boolean storageWritePermissionGranted;
    private boolean cameraPermissionGranted;

    private File imageFolder;
    private File lastImageFile;

    // ImageAdapter gets images from folder and supplies GridView with ImageViews to display
    private class ImageAdapter extends BaseAdapter {

        private Context context;
        private File[] imageFiles;

        public ImageAdapter(Context c) {
            this.context = c;
            imageFiles = imageFolder.listFiles();
            Arrays.sort(imageFiles, Collections.reverseOrder());
        }

        @Override
        public int getCount() {
            return imageFiles.length;
        }

        @Override
        public File getItem(int position) {
            if (position < 0 || position >= imageFiles.length) {
                return null;
            } else {
                return imageFiles[position];
            }
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(int position, View recycledView, ViewGroup parent) {
            ImageView imageView;
            if (recycledView == null) {
                imageView = new ImageView(context);
            } else {
                imageView = (ImageView) recycledView;
            }

            String imageUri = imageFiles[position].toString();

            int thumbnailSize = ((GridView) parent).getColumnWidth();
            imageView.setLayoutParams(new GridView.LayoutParams(thumbnailSize, thumbnailSize));

            Glide.with(context)
                    .load(imageUri)
                    .apply(centerCropTransform())
                    .into(imageView);

            return imageView;
        }

        @Override
        public void notifyDataSetChanged() {
            imageFiles = imageFolder.listFiles();
            Arrays.sort(imageFiles, Collections.reverseOrder());
            super.notifyDataSetChanged();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        checkPermissions();

        if (storageWritePermissionGranted) {
            setupImageGrid();
        }

        cameraButton = findViewById(R.id.button_camera);
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if ((!storageWritePermissionGranted) || (!cameraPermissionGranted)) {
                    checkPermissions();
                    return;
                }
                dispatchCaptureIntent();
            }
        });
    }

    private void setupImageGrid() {
        createImageFolder();
        imageGridView = findViewById(R.id.gridview_gallery);
        imageGridView.setAdapter(new ImageAdapter(this));
        imageGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File clickedImage = (File) parent.getItemAtPosition(position);
                Intent displayImageIntent = new Intent(getApplicationContext(), DecodingActivity.class);
                displayImageIntent.setData(Uri.fromFile(clickedImage));
                displayImageIntent.putExtra("imageFolder", Uri.fromFile(imageFolder));
                startActivity(displayImageIntent);
            }
        });
    }

    private void dispatchCaptureIntent() {
        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (captureIntent.resolveActivity(getPackageManager()) != null) {
            lastImageFile = createImageFile(DateTime.now());
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(lastImageFile));
            startActivityForResult(captureIntent, REQUEST_CAPTURE_IMAGE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CAPTURE_IMAGE:
                if (resultCode == RESULT_OK) {
                    // rename image file so that timestamp is correct
                    DateTime lastModified = new DateTime(lastImageFile.lastModified());
                    if (lastImageFile.renameTo(createImageFile(lastModified))) {
                        ((ImageAdapter) imageGridView.getAdapter()).notifyDataSetChanged();
                    } else {
                        Toast.makeText(this, "Renaming failed, timestamp of image " +
                                "in 'Beetags' folder may be wrong.", Toast.LENGTH_LONG).show();
                        ((ImageAdapter) imageGridView.getAdapter()).notifyDataSetChanged();
                    }
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (imageGridView != null) {
            ((ImageAdapter) imageGridView.getAdapter()).notifyDataSetChanged();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        /*
        View decorView = getWindow().getDecorView();
        if (hasFocus) {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
        */
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length <= 0) return;
        switch (requestCode) {
            case REQUEST_PERMISSIONS:
                for (int i = 0; i < grantResults.length; i++) {
                     if (permissions[i].equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                         if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                             storageWritePermissionGranted = true;
                             setupImageGrid();
                         }
                     } else if (permissions[i].equals(Manifest.permission.CAMERA)) {
                         if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                             cameraPermissionGranted = true;
                         }
                     }
                }
                break;
        }
    }

    private void createImageFolder() {
        File publicPictureFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        imageFolder = new File(publicPictureFolder, "Beetags");
        if (!imageFolder.exists()) {
            imageFolder.mkdirs();
        }
    }

    private File createImageFile(DateTime date) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd_HH-mm-ss");
        String fileName = "bees_" + formatter.print(date);
        File file = new File(imageFolder.getPath(), fileName + ".jpg");
        if (file.exists()) {
            int i = 0;
            file = new File(imageFolder.getPath(), fileName + "_" + i + ".jpg");
            while (file.exists()) {
                i++;
                file = new File(imageFolder.getPath(), fileName + "_" + i + ".jpg");
            }
        }
        return file;
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> permissions = new ArrayList<String>();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED) {
                cameraPermissionGranted = true;
            } else {
                cameraPermissionGranted = false;
                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    Toast.makeText(this, "If you want to take your own pictures, this app needs " +
                            "access to the camera.", Toast.LENGTH_LONG).show();
                }
                permissions.add(Manifest.permission.CAMERA);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED) {
                storageWritePermissionGranted = true;
            } else {
                storageWritePermissionGranted = false;
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, "This app needs access to external storage to store the image files.",
                            Toast.LENGTH_LONG).show();
                }
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (!permissions.isEmpty()) {
                requestPermissions(permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
            }
        } else {
            // in this case we can just act as if permission was granted
            cameraPermissionGranted = true;
            storageWritePermissionGranted = true;
        }
    }
}
