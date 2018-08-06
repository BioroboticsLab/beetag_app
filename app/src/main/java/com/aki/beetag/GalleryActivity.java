package com.aki.beetag;

import android.Manifest;
import android.app.DialogFragment;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

import static com.bumptech.glide.request.RequestOptions.centerCropTransform;

// Main activity

public class GalleryActivity
        extends AppCompatActivity
        implements
            ImageDeletionConfirmationDialogFragment.OnImageDeletionConfirmedListener,
            DatabaseShareDialogFragment.OnDatabaseShareListener {

    // constants
    private static final int REQUEST_PERMISSIONS = 2;
    private static final int REQUEST_CAPTURE_IMAGE = 3;
    private static final int REQUEST_SHARE_DATABASE = 4;

    // launches the camera app
    private ImageButton cameraButton;
    // exits image selection mode
    private ImageButton cancelSelectionButton;
    // deletes selected images
    private ImageButton deleteImagesWithTagsButton;
    // opens settings
    private ImageButton settingsButton;
    // contains square thumbnails
    private GridView imageGridView;
    // message that is displayed if folder contains no images
    private TextView noImagesTextView;

    private boolean storageWritePermissionGranted;
    private boolean cameraPermissionGranted;

    private TagDatabase database;
    private TagDao dao;

    private File imageFolder;
    private File[] imageFiles;
    private ArrayList<File> selectedImageFiles;
    private File lastImageFile;

    // Standard mode:  tapping on a thumbnail launches DecodingActivity
    // Selection mode: tapping on thumbnails selects the images (e.g. for deletion)
    private enum UserMode {
        STANDARD_MODE, SELECTION_MODE
    }
    private UserMode userMode;

    private ImageAdapter imageAdapter;

    // Comparator that is used to reverse sort the images after "last modified" date (newest on top)
    private Comparator<File> reverseFileDateComparator = new Comparator<File>() {
        @Override
        public int compare(File l, File r) {
            return Long.compare(r.lastModified(), l.lastModified());
        }
    };

    // AsyncTask that fetches the amount of tags marked on each image from the tag database
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
            if (imageCount != 0) {
                int[] tagCounts = new int[imageCount];
                for (int i = 0; i < imageCount; i++) {
                    tagCounts[i] = counts[i];
                }
                imageAdapter.updateTagCounts(tagCounts);
            }
        }
    }

    // AsyncTask that deletes all tag entries that belong to the specified image files
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
            noImagesTextView.setVisibility(imageFiles.length == 0 ? View.VISIBLE : View.INVISIBLE);
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

            if (tagCounts != null && tagCounts.length > position) {
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
            noImagesTextView.setVisibility(imageFiles.length == 0 ? View.VISIBLE : View.INVISIBLE);
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

                Bundle argumentBundle = new Bundle();
                argumentBundle.putString("databasePath", getDatabasePath("beetag-database").getAbsolutePath());
                SettingsFragment settingsFragment = new SettingsFragment();
                settingsFragment.setArguments(argumentBundle);

                getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, settingsFragment)
                        .addToBackStack("settings")
                        .commit();
            }
        });

        setUserMode(UserMode.STANDARD_MODE);

        // After the initialization, check possible image transfer requests.
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (type.startsWith("image/")) {
                onReceiveImageIntent(intent);
            }
        }
    }

    // called by TagDeletionConfirmationDialogFragment when the user confirms the deletion of images
    @Override
    public void onImageDeletionConfirmed() {
        new DeleteTagsOnImagesTask().execute(selectedImageFiles.toArray(new File[selectedImageFiles.size()]));
    }

    @Override
    public void onDatabaseShare(File file) {
        Uri databaseUri = FileProvider.getUriForFile(getApplicationContext(),
                BuildConfig.APPLICATION_ID + ".fileprovider",
                file);
        Intent exportIntent = new Intent();
        exportIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        exportIntent.setData(databaseUri);
        exportIntent.putExtra(Intent.EXTRA_STREAM, databaseUri);
        startActivityForResult(exportIntent, REQUEST_SHARE_DATABASE);
    }

    private void setupImageGrid() {
        createImageFolder();

        noImagesTextView = findViewById(R.id.textview_gallery_no_images);
        noImagesTextView.setVisibility(View.INVISIBLE);

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

    // opens the camera app
    private void dispatchCaptureIntent() {
        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (captureIntent.resolveActivity(getPackageManager()) != null) {
            lastImageFile = createImageFile(DateTime.now());
            Uri lastImageUri = FileProvider.getUriForFile(this,
                    BuildConfig.APPLICATION_ID + ".fileprovider",
                    lastImageFile);
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, lastImageUri);
            if (captureIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(captureIntent, REQUEST_CAPTURE_IMAGE);
            } else {
                Toast.makeText(
                        getApplicationContext(),
                        "Could not open camera application.",
                        Toast.LENGTH_LONG).show();
            }
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
        return createImageFile(fileName);
    }
    private File createImageFile(String fileName) {
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
            // on Marshmallow and later, we need to explicitly check permissions
            ArrayList<String> permissions = new ArrayList<>();
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

    private void onReceiveImageIntent(Intent intent) {
        Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);

        if (imageUri == null) {
            Toast.makeText(
                getApplicationContext(),
                "No image received.",
                Toast.LENGTH_LONG).show();
            return;
        }
        try {
            final boolean saved = saveFile(imageUri);
            if (saved) {
                Toast.makeText(
                        getApplicationContext(),
                        "Image saved.",
                        Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            Toast.makeText(
                getApplicationContext(),
                "Error saving file: " + e.getLocalizedMessage(),
                Toast.LENGTH_LONG).show();
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

    // Saves an image from an Uri to the Beetags picture folder.
    private boolean saveFile(Uri sourceUri) throws IOException {
        Cursor returnCursor = null;
        String fileDisplayName = "";
        // Get the filename from the info table.
        try {
            returnCursor = getContentResolver().query(sourceUri,
                    null, null, null, null);
            if (returnCursor != null && returnCursor.moveToFirst()) {
                final int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                fileDisplayName = returnCursor.getString(nameIndex);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not save image.",
                    Toast.LENGTH_LONG).show();
            return false;
        } finally {
            if (returnCursor != null)
                returnCursor.close();
        }

        // Cut the ending from the filename to be able to insert a count for duplicate images.
        final int fileTypeStart = fileDisplayName.lastIndexOf('.');
        if (fileTypeStart != -1) {
            final String fileType = fileDisplayName.substring(fileTypeStart).toLowerCase();
            if (fileType.equals(".jpeg") || fileType.equals(".jpg")) {
                fileDisplayName = fileDisplayName.substring(0, fileTypeStart);
            }
            else {
                Toast.makeText(this, "Only jpg images allowed.",
                        Toast.LENGTH_LONG).show();
                return false;
            }
        }

        // And copy the stream to the file location.
        File destination = createImageFile(fileDisplayName);
        FileChannel dst = new FileOutputStream(destination).getChannel();

        InputStream srcStream = getContentResolver().openInputStream(sourceUri);
        ReadableByteChannel src = Channels.newChannel(srcStream);

        dst.transferFrom(src, 0, srcStream.available());
        src.close();
        dst.close();
        return true;
    }
}
