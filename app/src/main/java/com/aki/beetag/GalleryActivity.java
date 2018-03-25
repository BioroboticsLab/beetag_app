package com.aki.beetag;

import android.Manifest;
import android.app.DialogFragment;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import static com.bumptech.glide.request.RequestOptions.centerCropTransform;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;


public class GalleryActivity
        extends AppCompatActivity
        implements ImageDeletionConfirmationDialogFragment.OnImageDeletionConfirmedListener {

    private static final int REQUEST_PERMISSIONS = 2;
    private static final int REQUEST_CAPTURE_IMAGE = 3;

    private ImageButton cameraButton;
    private ImageButton cancelSelectionButton;
    private ImageButton deleteImagesWithTagsButton;
    private ImageButton settingsButton;
    private GridView imageGridView;

    private boolean storageWritePermissionGranted;
    private boolean cameraPermissionGranted;

    private TagDatabase database;
    private TagDao dao;

    private File imageFolder;
    private File[] imageFiles;
    private ArrayList<File> selectedImageFiles;
    private File lastImageFile;

    private enum UserMode {
        STANDARD_MODE, SELECTION_MODE
    }
    private UserMode userMode;

    private ImageAdapter imageAdapter;

    private Comparator<File> reverseFileDateComparator = new Comparator<File>() {
        @Override
        public int compare(File l, File r) {
            return Long.compare(r.lastModified(), l.lastModified());
        }
    };

    private class GetTagCountsTask extends AsyncTask<File[], Void, Integer[]> {
        @Override
        protected Integer[] doInBackground(File[]... files) {
            File[] imageFiles = files[0];
            int fileCount = imageFiles.length;
            Integer[] tagCounts = new Integer[fileCount];
            for (int i = 0; i < fileCount; i++) {
                String imageName = imageFiles[i].getName();
                if (dao == null || database == null) {
                    cancel(true);
                }
                tagCounts[i] = dao.getTagCount(imageName);
            }
            return tagCounts;
        }

        @Override
        protected void onPostExecute(Integer[] counts) {
            int imageCount = counts.length;
            int[] tagCounts = new int[imageCount];
            for (int i = 0; i < imageCount; i++) {
                tagCounts[i] = counts[i];
            }
            imageAdapter.updateTagCounts(tagCounts);
        }
    }

    private class DeleteTagsOnImagesTask extends AsyncTask<File[], Void, File[]> {
        @Override
        protected File[] doInBackground(File[]... files) {
            File[] images = files[0];
            for (File f : images) {
                if (dao == null || database == null) {
                    cancel(true);
                }
                dao.deleteAllTagsOnImage(f.getName());
            }
            return images;
        }

        @Override
        protected void onPostExecute(File[] files) {
            boolean fileDeletionSuccess = true;
            for (File f : files) {
                if (!f.delete()) {
                    fileDeletionSuccess = false;
                }
            }
            if (imageAdapter != null) {
                imageAdapter.notifyDataSetChanged();
            }
            setUserMode(UserMode.STANDARD_MODE);

            if (fileDeletionSuccess) {
                Toast.makeText(
                        getApplicationContext(),
                        "Images and tags deleted.",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(
                        getApplicationContext(),
                        "Tags have been deleted, but something went wrong while "
                                + "deleting the image files. Please delete these yourself.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ImageAdapter gets images from folder and supplies GridView with Views to display
    private class ImageAdapter extends BaseAdapter {

        private Context context;
        private int[] tagCounts;

        public ImageAdapter(Context c) {
            this.context = c;
            imageFiles = imageFolder.listFiles();
            selectedImageFiles = new ArrayList<>();
            Arrays.sort(imageFiles, reverseFileDateComparator);
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
            View thumbnailView;
            if (recycledView == null) {
                LayoutInflater inflater =
                        (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                thumbnailView = inflater.inflate(R.layout.layout_gallery_thumbnail, parent, false);
            } else {
                thumbnailView = recycledView;
            }

            int thumbnailSize = ((GridView) parent).getColumnWidth();
            thumbnailView.setLayoutParams(new GridView.LayoutParams(thumbnailSize, thumbnailSize));

            ImageView thumbnailImageView = thumbnailView.findViewById(R.id.imageview_gallery_thumbnail);
            thumbnailImageView.getLayoutParams().width = thumbnailSize;
            thumbnailImageView.getLayoutParams().height = thumbnailSize;
            if (!thumbnailImageView.isInLayout()) {
                thumbnailImageView.requestLayout();
            }

            String imagePath = Uri.fromFile(imageFiles[position]).getPath();
            Glide.with(context)
                    .load(imagePath)
                    .apply(centerCropTransform())
                    .into(thumbnailImageView);

            View thumbnailSelectionOverlay = thumbnailView.findViewById(R.id.view_gallery_thumbnail_selection_overlay);
            if (userMode == UserMode.SELECTION_MODE && selectedImageFiles.contains(imageFiles[position])) {
                thumbnailSelectionOverlay.setVisibility(View.VISIBLE);
            } else {
                thumbnailSelectionOverlay.setVisibility(View.INVISIBLE);
            }

            if (tagCounts != null) {
                TextView thumbnailTextView = thumbnailView.findViewById(R.id.textview_gallery_thumbnail_tagcount);
                if (tagCounts[position] != 0) {
                    thumbnailTextView.setText(String.format(Locale.US, "%d", tagCounts[position]));
                    thumbnailTextView.setVisibility(View.VISIBLE);
                } else {
                    thumbnailTextView.setVisibility(View.INVISIBLE);
                }
            }

            return thumbnailView;
        }

        @Override
        public void notifyDataSetChanged() {
            imageFiles = imageFolder.listFiles();
            Arrays.sort(imageFiles, reverseFileDateComparator);
            new GetTagCountsTask().execute(imageFiles);
            super.notifyDataSetChanged();
        }

        public void updateTagCounts(int[] tagCounts) {
            this.tagCounts = tagCounts;
            super.notifyDataSetChanged();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        RoomDatabase.Builder<TagDatabase> databaseBuilder = Room.databaseBuilder(getApplicationContext(), TagDatabase.class, "beetag-database");
        databaseBuilder.fallbackToDestructiveMigration();
        database = databaseBuilder.build();
        dao = database.getDao();

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

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

        cancelSelectionButton = findViewById(R.id.button_selection_cancel);
        cancelSelectionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if ((!storageWritePermissionGranted) || (!cameraPermissionGranted)) {
                    checkPermissions();
                    return;
                }
                setUserMode(UserMode.STANDARD_MODE);
            }
        });

        deleteImagesWithTagsButton = findViewById(R.id.button_delete_images_with_tags);
        deleteImagesWithTagsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if ((!storageWritePermissionGranted) || (!cameraPermissionGranted)) {
                    checkPermissions();
                    return;
                }
                if (userMode != UserMode.SELECTION_MODE) {
                    return;
                }

                DialogFragment confirmationFragment = new ImageDeletionConfirmationDialogFragment();
                confirmationFragment.show(getFragmentManager(), "imageDeletionConfirmationDialog");
            }
        });

        settingsButton = findViewById(R.id.button_settings);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (userMode != UserMode.STANDARD_MODE) {
                    return;
                }

                getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, new SettingsFragment())
                        .addToBackStack("settings")
                        .commit();
            }
        });

        setUserMode(UserMode.STANDARD_MODE);
    }

    @Override
    public void onImageDeletionConfirmed() {
        new DeleteTagsOnImagesTask().execute(selectedImageFiles.toArray(new File[selectedImageFiles.size()]));
    }

    private void setupImageGrid() {
        createImageFolder();
        imageGridView = findViewById(R.id.gridview_gallery);
        imageAdapter = new ImageAdapter(this);
        imageGridView.setAdapter(imageAdapter);
        imageGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (userMode == UserMode.STANDARD_MODE) {
                    File clickedImage = (File) parent.getItemAtPosition(position);
                    Intent displayImageIntent = new Intent(getApplicationContext(), DecodingActivity.class);
                    displayImageIntent.setData(Uri.fromFile(clickedImage));
                    displayImageIntent.putExtra("imageFolder", Uri.fromFile(imageFolder));
                    startActivity(displayImageIntent);
                } else if (userMode == UserMode.SELECTION_MODE) {
                    if (selectedImageFiles.contains(imageFiles[position])) {
                        selectedImageFiles.remove(imageFiles[position]);
                        imageGridView.invalidateViews();
                    } else {
                        selectedImageFiles.add(imageFiles[position]);
                        imageGridView.invalidateViews();
                    }
                }
            }
        });
        imageGridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
                if (userMode == UserMode.STANDARD_MODE) {
                    selectedImageFiles.add(imageFiles[position]);
                    setUserMode(UserMode.SELECTION_MODE);
                    return true;
                } else {
                    return false;
                }
            }
        });
        new GetTagCountsTask().execute(imageFolder.listFiles());
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
                    if (!lastImageFile.renameTo(createImageFile(lastModified))) {
                        Toast.makeText(this, "Renaming failed, timestamp of image " +
                                "in 'Beetags' folder may be wrong.", Toast.LENGTH_LONG).show();
                    }
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (imageAdapter != null) {
            imageAdapter.notifyDataSetChanged();
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

    // sets the user mode and changes UI accordingly
    private void setUserMode(UserMode userMode) {
        this.userMode = userMode;
        switch (userMode) {
            case STANDARD_MODE:
                if (selectedImageFiles != null) {
                    selectedImageFiles.clear();
                }
                cameraButton.setVisibility(View.VISIBLE);
                cancelSelectionButton.setVisibility(View.INVISIBLE);
                settingsButton.setVisibility(View.VISIBLE);
                deleteImagesWithTagsButton.setVisibility(View.INVISIBLE);
                if (imageGridView != null) {
                    imageGridView.invalidateViews();
                }
                break;
            case SELECTION_MODE:
                cameraButton.setVisibility(View.INVISIBLE);
                cancelSelectionButton.setVisibility(View.VISIBLE);
                settingsButton.setVisibility(View.INVISIBLE);
                deleteImagesWithTagsButton.setVisibility(View.VISIBLE);
                if (imageGridView != null) {
                    imageGridView.invalidateViews();
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

    @Override
    protected void onStop() {
        if (database != null) {
            database.close();
        }
        dao = null;
        super.onStop();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        database = Room.databaseBuilder(getApplicationContext(), TagDatabase.class, "beetag-database").build();
        dao = database.getDao();
    }
}
