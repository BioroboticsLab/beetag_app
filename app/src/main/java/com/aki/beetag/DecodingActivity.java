package com.aki.beetag;

import android.app.Activity;
import android.app.DialogFragment;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import org.joda.time.DateTime;
import org.joda.time.MutableDateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageTypeException;
import org.msgpack.core.MessageUnpacker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.PngWriter;

// This is the activity that launches when tapping on one of the thumbnails in GalleryActivity.

public class DecodingActivity
        extends Activity
        implements
            TagTimePickerFragment.OnTagTimePickedListener,
            TagDatePickerFragment.OnTagDatePickedListener,
            TagDeletionConfirmationDialogFragment.OnTagDeletionConfirmedListener {

    // Tagging mode: the user can move the image around and mark tags
    // Editing mode: one tag is displayed, the user can edit its data
    public enum ViewMode {
        TAGGING_MODE, EDITING_MODE
    }
    private ViewMode viewMode;

    private TagView tagView;
    private RelativeLayout tagInfoLayout;
    private ScrollView tagInfoScrollView;
    private FloatingActionButton tagButton;
    private ImageButton cancelTagEditingButton;
    private ImageButton deleteTagButton;
    private ImageButton saveEditedTagButton;
    private TextView textInputDoneButton;

    private TextView beeIdTextView;
    private EditText tagLabelEditText;
    private EditText tagNotesEditText;
    private TextView tagDateTextView;
    private TextView tagTimeTextView;
    private TextView detectionIdTextView;

    private File imageFolder;
    private Uri imageUri;

    private TagDatabase database = null;
    private TagDao dao;

    private SharedPreferences sharedPreferences;

    // class that supplies the bee names
    private BeeNamer beeNamer;

    // tag that is currently being edited (in editing mode)
    private Tag currentlyEditedTag;
    private double dragStartingAngle;
    private double tagOrientationBeforeDrag;

    // AsyncTask that issues a request to the decoding server and processes the response
    private class ServerRequestTask extends AsyncTask<DecodingData, Void, DecodingResult> {
        @Override
        protected DecodingResult doInBackground(DecodingData... decodingData) {
            DecodingData data = decodingData[0];

            // find out dimensions of image
            BitmapFactory.Options options = new BitmapFactory.Options();
            Bitmap imageBitmap = BitmapFactory.decodeFile(new File(data.imageUri.getPath()).getAbsolutePath(), options);
            int imageWidth = options.outWidth;
            int imageHeight = options.outHeight;

            // matrix for the sent image to be correctly rotated and scaled
            Matrix transformationMatrix = new Matrix();
            transformationMatrix.postRotate(data.appliedOrientation);
            int tagSizeTargetInPx = 50;
            float tagScaleToTarget = tagSizeTargetInPx / data.tagSizeInPx;
            transformationMatrix.postScale(tagScaleToTarget, tagScaleToTarget);

            Bitmap finalImage;

            if (data.mode == DecodingData.DetectionMode.AUTOMATIC) {
                finalImage = Bitmap.createBitmap(
                        imageBitmap,
                        0,
                        0,
                        imageWidth,
                        imageHeight,
                        transformationMatrix,
                        false);
            } else {
                // reposition tagCenter based on rotation of image
                PointF tagCenter = new PointF();
                switch (data.appliedOrientation) {
                    case 90:
                        // rotate tagCenter by 90 degrees, counter-clockwise
                        tagCenter.set(data.tagCenter.y, imageHeight-data.tagCenter.x);
                        break;
                    case 180:
                        // rotate tagCenter by 180 degrees
                        tagCenter.set(imageWidth-data.tagCenter.x, imageHeight-data.tagCenter.y);
                        break;
                    case 270:
                        // rotate tagCenter by 90 degrees, clockwise
                        tagCenter.set(imageWidth-data.tagCenter.y, data.tagCenter.x);
                        break;
                    default:
                        tagCenter.set(data.tagCenter);
                        break;
                }

                ImageSquare cropSquare = new ImageSquare(tagCenter, data.tagSizeInPx * 2f);
                Rect imageCropZone = cropSquare.getImageOverlap(imageWidth, imageHeight);
                finalImage = Bitmap.createBitmap(
                        imageBitmap,
                        imageCropZone.left,
                        imageCropZone.top,
                        imageCropZone.right - imageCropZone.left,
                        imageCropZone.bottom - imageCropZone.top,
                        transformationMatrix,
                        false);
            }

            // Dimensions of the final image that is sent for detection/decoding.
            // For a single decoding (without the server-side detection of tags), the image
            // must have a size of 100x100. In case of server-side tag detection, the
            // entire image is sent.
            int finalImageWidth;
            int finalImageHeight;
            if (data.mode == DecodingData.DetectionMode.AUTOMATIC) {
                finalImageWidth = finalImage.getWidth();
                finalImageHeight = finalImage.getHeight();
            } else {
                finalImageWidth = 100;
                finalImageHeight = 100;
            }

            // Intermediate stream that the PNG is written to,
            // to find out the overall data size.
            // After writing, the connection is opened with the
            // appropriate data length.
            // (ChunkedStreamingMode is not supported by the bb_pipeline_api,
            // so we have to use FixedLengthStreamingMode)
            ByteArrayOutputStream pngStream = new ByteArrayOutputStream();

            // single channel PNG (8 bit, no alpha, grayscale, not indexed)
            ImageInfo imgInfo = new ImageInfo(finalImageWidth, finalImageHeight, 8, false, true, false);
            PngWriter pngWriter = new PngWriter(pngStream, imgInfo);

            int[] grayLine = new int[finalImageWidth];
            for (int y = 0; y < finalImageHeight; y++) {
                // write PNG line by line
                for (int x = 0; x < finalImageWidth; x++) {
                    int grayValue;
                    // add padding if image size is not correct
                    if (x < finalImageWidth && y < finalImageHeight) {
                        int pixel = finalImage.getPixel(x, y);
                        // convert pixel to grayscale
                        grayValue = (int) (0.2125 * ((pixel >> 16) & 0xff));
                        grayValue += (int) (0.7154 * ((pixel >> 8) & 0xff));
                        grayValue += (int) (0.0721 * (pixel & 0xff));
                    } else {
                        grayValue = 0;
                    }
                    grayLine[x] = grayValue;
                }
                pngWriter.writeRowInt(grayLine);
            }
            pngWriter.end();

            BufferedOutputStream out = null;
            BufferedInputStream in = null;
            HttpURLConnection connection = null;
            List<Tag> tags = new ArrayList<>();
            int resultCode;

            try {
                connection = (HttpURLConnection) data.serverUrl.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/octet-stream");
                // ChunkedStreamingMode is not supported by the bb_pipeline_api, so use fixed length
                connection.setFixedLengthStreamingMode(pngStream.size());
                connection.setDoOutput(true);
                connection.setDoInput(true);

                out = new BufferedOutputStream(connection.getOutputStream());
                pngStream.writeTo(out);
                out.flush();
                in = new BufferedInputStream(connection.getInputStream());

                MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(in);
                int mapSize = unpacker.unpackMapHeader();
                ArrayList<ArrayList<Integer>> ids = new ArrayList<>();
                ArrayList<Double> orientations = new ArrayList<>();
                ArrayList<ArrayList<Float>> localizerPositions = new ArrayList<>();

                for (int i = 0; i < mapSize; i++) {
                    String key = unpacker.unpackString();
                    switch (key) {
                        case "IDs":
                            int idsListLength = unpacker.unpackArrayHeader();
                            ArrayList<Integer> id;
                            for (int j = 0; j < idsListLength; j++) {
                                id = new ArrayList<>();
                                // will always be 12 (12 bits)
                                int idLength = unpacker.unpackArrayHeader();
                                for (int k = 0; k < idLength; k++) {
                                    id.add((int) Math.round(unpacker.unpackDouble()));
                                }
                                ids.add(id);
                            }
                            break;
                        case "Orientations":
                            int orientationsListLength = unpacker.unpackArrayHeader();
                            for (int j = 0; j < orientationsListLength; j++) {
                                // will always be 3 (3 angle dimensions: z, y, x)
                                int orientationLength = unpacker.unpackArrayHeader();
                                for (int k = 0; k < orientationLength; k++) {
                                    // only use the z angle
                                    if (k == 0) {
                                        orientations.add(unpacker.unpackDouble());
                                    } else {
                                        unpacker.unpackDouble();
                                    }
                                }
                            }
                            break;
                        case "LocalizerPositions":
                            int localizerPositionsListLength = unpacker.unpackArrayHeader();
                            ArrayList<Float> localizerPosition;
                            for (int j = 0; j < localizerPositionsListLength; j++) {
                                localizerPosition = new ArrayList<>();
                                // will always be 2 (x and y coordinates)
                                int localizerPositionLength = unpacker.unpackArrayHeader();
                                for (int k = 0; k < localizerPositionLength; k++) {
                                    localizerPosition.add(unpacker.unpackFloat());
                                }
                                localizerPositions.add(localizerPosition);
                            }
                            break;
                    }
                }

                int tagCount;
                if (data.mode == DecodingData.DetectionMode.AUTOMATIC) {
                    tagCount = Math.min(localizerPositions.size(), Math.min(ids.size(), orientations.size()));
                } else {
                    tagCount = Math.min(ids.size(), orientations.size());
                }
                for (int i = 0; i < tagCount; i++) {
                    Tag tag = new Tag();
                    if (data.mode == DecodingData.DetectionMode.AUTOMATIC) {
                        tag.setCenterX(localizerPositions.get(i).get(1) / tagScaleToTarget);
                        tag.setCenterY(localizerPositions.get(i).get(0) / tagScaleToTarget);
                    } else {
                        tag.setCenterX(data.tagCenter.x);
                        tag.setCenterY(data.tagCenter.y);
                    }
                    tag.setRadius(data.tagSizeInPx / 2);
                    tag.setImageName(imageUri.getLastPathSegment());
                    tag.setOrientation(orientations.get(i));
                    tag.setBeeId(Tag.bitIdToDecimalId(ids.get(i)));
                    tag.setDate(new DateTime(new File(imageUri.getPath()).lastModified()));
                    tags.add(tag);
                }
            } catch (UnknownHostException e) {
                resultCode = DecodingResult.UNKNOWN_HOST;
                return new DecodingResult(data, tags, resultCode);
            } catch (IOException e) {
                resultCode = DecodingResult.COMMUNICATION_FAILED;
                return new DecodingResult(data, tags, resultCode);
            } catch (MessageTypeException e) {
                resultCode = DecodingResult.UNEXPECTED_RESPONSE;
                return new DecodingResult(data, tags, resultCode);
            } catch (Exception e) {
                resultCode = DecodingResult.UNKNOWN_ERROR;
                return new DecodingResult(data, tags, resultCode);
            } finally {
                closeWithoutIOException(out);
                closeWithoutIOException(in);
                if (connection != null) {
                    connection.disconnect();
                }
            }
            if (tags.isEmpty()) {
                resultCode = DecodingResult.TAG_NOT_FOUND;
            } else {
                resultCode = DecodingResult.OK;
            }
            return new DecodingResult(data, tags, resultCode);
        }

        // helper method to avoid nested try-catch-blocks
        private void closeWithoutIOException(Closeable c) {
            try {
                if (c != null) {
                    c.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected void onPostExecute(DecodingResult result) {
            switch (result.resultCode) {
                case DecodingResult.OK:
                    for (Tag resultTag : result.decodedTags) {
                        resultTag.setEntryId(UUID.randomUUID().toString());
                        resultTag.setLabel(sharedPreferences.getString("pref_default_label", ""));
                        // TODO: consider inserting all tags in a single task
                        new DatabaseInsertTask().execute(resultTag);
                    }
                    break;
                case DecodingResult.TAG_NOT_FOUND:
                    Toast.makeText(
                            getApplicationContext(),
                            "No tag found",
                            Toast.LENGTH_LONG).show();
                    insertDummyTag(
                            new PointF(result.input.tagCenter.x, result.input.tagCenter.y),
                            result.input.tagSizeInPx / 2);
                    break;
                case DecodingResult.COMMUNICATION_FAILED:
                    Toast.makeText(
                            getApplicationContext(),
                            "Server communication failed",
                            Toast.LENGTH_LONG).show();
                    insertDummyTag(
                            new PointF(result.input.tagCenter.x, result.input.tagCenter.y),
                            result.input.tagSizeInPx / 2);
                    break;
                case DecodingResult.UNKNOWN_HOST:
                    Toast.makeText(
                            getApplicationContext(),
                            "Could not resolve server address",
                            Toast.LENGTH_LONG).show();
                    insertDummyTag(
                            new PointF(result.input.tagCenter.x, result.input.tagCenter.y),
                            result.input.tagSizeInPx / 2);
                    break;
                case DecodingResult.UNEXPECTED_RESPONSE:
                    Toast.makeText(
                            getApplicationContext(),
                            "Unexpected response from server",
                            Toast.LENGTH_LONG).show();
                    insertDummyTag(
                            new PointF(result.input.tagCenter.x, result.input.tagCenter.y),
                            result.input.tagSizeInPx / 2);
                    break;
                case DecodingResult.UNKNOWN_ERROR:
                default:
                    Toast.makeText(
                            getApplicationContext(),
                            "Something went wrong :O",
                            Toast.LENGTH_LONG).show();
                    insertDummyTag(
                            new PointF(result.input.tagCenter.x, result.input.tagCenter.y),
                            result.input.tagSizeInPx / 2);
                    break;
            }
        }
    }

    // AsyncTask that fetches the tags that belong to an image from the database
    private class GetTagsTask extends AsyncTask<Uri, Void, List<Tag>> {
        @Override
        protected List<Tag> doInBackground(Uri... uris) {
            if (dao == null || database == null) {
                cancel(true);
            }
            return dao.loadTagsByImage(uris[0].getLastPathSegment());
        }

        @Override
        protected void onPostExecute(List<Tag> tags) {
            tagView.setTagsOnImage(tags);
        }
    }

    // AsyncTask that inserts a tag into the database
    private class DatabaseInsertTask extends AsyncTask<Tag, Void, Void> {
        @Override
        protected Void doInBackground(Tag... tags) {
            if (dao == null || database == null) {
                cancel(true);
            }
            dao.insertTags(tags[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            new GetTagsTask().execute(imageUri);
        }
    }

    // AsyncTask that deletes a tag from the database
    private class DatabaseDeleteTask extends AsyncTask<Tag, Void, Void> {
        @Override
        protected Void doInBackground(Tag... tags) {
            if (dao == null || database == null) {
                cancel(true);
            }
            dao.deleteTags(tags[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            new GetTagsTask().execute(imageUri);
        }
    }

    // AsyncTask that updates a tag in the database
    private class DatabaseUpdateTask extends AsyncTask<Tag, Void, Void> {
        @Override
        protected Void doInBackground(Tag... tags) {
            if (dao == null || database == null) {
                cancel(true);
            }
            dao.updateTags(tags[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            Toast.makeText(getApplicationContext(), "Tag saved.", Toast.LENGTH_SHORT).show();
            new GetTagsTask().execute(imageUri);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_decoding);

        Intent intent = getIntent();
        imageUri = intent.getData();
        imageFolder = new File(((Uri) intent.getExtras().get("imageFolder")).getPath());

        RoomDatabase.Builder<TagDatabase> databaseBuilder = Room.databaseBuilder(getApplicationContext(), TagDatabase.class, "beetag-database");
        databaseBuilder.fallbackToDestructiveMigration();
        database = databaseBuilder.build();
        dao = database.getDao();

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        beeNamer = new BeeNamer();

        tagInfoLayout = findViewById(R.id.relativelayout_tag_info);
        tagInfoScrollView = findViewById(R.id.scrollview_tag_info);

        View.OnFocusChangeListener onFocusChangeListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    cancelTagEditingButton.setVisibility(View.INVISIBLE);
                    deleteTagButton.setVisibility(View.INVISIBLE);
                    saveEditedTagButton.setVisibility(View.INVISIBLE);
                    textInputDoneButton.setVisibility(View.VISIBLE);
                } else {
                    cancelTagEditingButton.setVisibility(View.VISIBLE);
                    deleteTagButton.setVisibility(View.VISIBLE);
                    saveEditedTagButton.setVisibility(View.VISIBLE);
                    textInputDoneButton.setVisibility(View.INVISIBLE);
                }
            }
        };
        tagLabelEditText = findViewById(R.id.edittext_tag_info_label);
        tagLabelEditText.setOnFocusChangeListener(onFocusChangeListener);
        tagNotesEditText = findViewById(R.id.edittext_tag_info_notes);
        tagNotesEditText.setOnFocusChangeListener(onFocusChangeListener);

        beeIdTextView = findViewById(R.id.textview_tag_info_bee_id);

        tagDateTextView = findViewById(R.id.textview_tag_info_date);
        tagDateTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!tagView.isReady() || viewMode != ViewMode.EDITING_MODE) {
                    return;
                }

                DialogFragment tagDatePickerFragment = new TagDatePickerFragment();
                tagDatePickerFragment.show(getFragmentManager(), "tagDatePicker");
            }
        });

        tagTimeTextView = findViewById(R.id.textview_tag_info_time);
        tagTimeTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!tagView.isReady() || viewMode != ViewMode.EDITING_MODE) {
                    return;
                }

                DialogFragment tagTimePickerFragment = new TagTimePickerFragment();
                tagTimePickerFragment.show(getFragmentManager(), "tagTimePicker");
            }
        });

        detectionIdTextView = findViewById(R.id.textview_tag_info_detection_id);

        tagButton = findViewById(R.id.button_tag);
        tagButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!tagView.isReady() || viewMode != ViewMode.TAGGING_MODE) {
                    return;
                }

                PointF tagCenter = tagView.getCenter();
                float tagSizeInPx = (tagView.getTagCircleRadius() * 2) / tagView.getScale();
                int appliedOrientation = tagView.getAppliedOrientation();

                if (sharedPreferences.getBoolean("pref_online_decoding_enabled", false)) {
                    try {
                        URL serverUrl;
                        serverUrl = buildUrl();
                        DecodingData.DetectionMode mode;
                        if (sharedPreferences.getBoolean("pref_detect_all_tags", false)) {
                            mode = DecodingData.DetectionMode.AUTOMATIC;
                        } else {
                            mode = DecodingData.DetectionMode.SINGLE;
                        }
                        DecodingData tagData = new DecodingData(
                                mode,
                                serverUrl,
                                imageUri,
                                tagCenter,
                                tagSizeInPx,
                                appliedOrientation);
                        new ServerRequestTask().execute(tagData);
                    } catch (JSONException | MalformedURLException | UnsupportedEncodingException e) {
                        Toast.makeText(getApplicationContext(), "Invalid server URL", Toast.LENGTH_SHORT).show();
                        insertDummyTag(tagCenter, tagSizeInPx / 2);
                    }
                } else {
                    insertDummyTag(tagCenter, tagSizeInPx / 2);
                }
            }
        });

        cancelTagEditingButton = findViewById(R.id.button_tag_info_cancel);
        cancelTagEditingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!tagView.isReady() || viewMode != ViewMode.EDITING_MODE) {
                    return;
                }

                new GetTagsTask().execute(imageUri);
                setViewMode(ViewMode.TAGGING_MODE);
            }
        });

        deleteTagButton = findViewById(R.id.button_delete_tag);
        deleteTagButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!tagView.isReady() || viewMode != ViewMode.EDITING_MODE) {
                    return;
                }

                if (currentlyEditedTag != null) {
                    DialogFragment confirmationDialog = new TagDeletionConfirmationDialogFragment();
                    confirmationDialog.show(getFragmentManager(), "tagDeletionConfirmationDialog");
                }
            }
        });

        textInputDoneButton = findViewById(R.id.button_text_input_done);
        textInputDoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!tagView.isReady() || viewMode != ViewMode.EDITING_MODE) {
                    return;
                }

                currentlyEditedTag.setLabel(tagLabelEditText.getText().toString());
                currentlyEditedTag.setNotes(tagNotesEditText.getText().toString());

                View focusedView = getCurrentFocus();
                if (focusedView != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
                    }
                    tagInfoLayout.requestFocus();
                }
                tagInfoScrollView.smoothScrollTo(0, 0);
            }
        });

        saveEditedTagButton = findViewById(R.id.button_save_edited_tag);
        saveEditedTagButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!tagView.isReady() || viewMode != ViewMode.EDITING_MODE) {
                    return;
                }

                if (currentlyEditedTag != null) {
                    new DatabaseUpdateTask().execute(currentlyEditedTag);
                    setViewMode(ViewMode.TAGGING_MODE);
                }
            }
        });

        tagView = findViewById(R.id.tag_view);
        tagView.setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
        tagView.setMinimumDpi(10);
        tagView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER);

        final GestureDetector gestureDetector = new GestureDetector(getApplicationContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (!tagView.isReady()) {
                    return false;
                }

                PointF tap = tagView.viewToSourceCoord(e.getX(), e.getY());
                if (viewMode == ViewMode.TAGGING_MODE) {
                    Tag tappedTag = tagView.tagAtPosition(tap);
                    if (tappedTag != null) {
                        currentlyEditedTag = tappedTag;
                        setViewMode(ViewMode.EDITING_MODE);
                    } else {
                        return false;
                    }
                } else if (viewMode == ViewMode.EDITING_MODE) {
                    int toggledBitPosition = tagView.bitSegmentAtPosition(tap, currentlyEditedTag);
                    ArrayList<Integer> id = Tag.decimalIdToBitId(currentlyEditedTag.getBeeId());
                    if (toggledBitPosition != -1) {
                        // invert bit that was tapped
                        id.set(toggledBitPosition, 1 - id.get(toggledBitPosition));
                        currentlyEditedTag.setBeeId(Tag.bitIdToDecimalId(id));
                        // update view to show changed tag
                        tagView.invalidate();

                        // if bee name should be displayed, append it to ID
                        String beeNameSuffix;
                        if (sharedPreferences.getBoolean("pref_display_bee_name", true)) {
                            beeNameSuffix = String.format(
                                    getResources().getString(R.string.bee_name_appended),
                                    beeNamer.getBeeName(currentlyEditedTag.getBeeId()));
                        } else {
                            beeNameSuffix = "";
                        }
                        beeIdTextView.setText(String.format(
                                getResources().getString(R.string.bee_id),
                                currentlyEditedTag.getBeeId(),
                                beeNameSuffix));
                    } else {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (!tagView.isReady()) {
                    return;
                }

                if (viewMode == ViewMode.EDITING_MODE) {
                    if (Build.VERSION.SDK_INT < 24) {
                        tagView.startDrag(
                                null,
                                new View.DragShadowBuilder(),
                                null,
                                0);
                    } else {
                        tagView.startDragAndDrop(
                                null,
                                new View.DragShadowBuilder(),
                                null,
                                0);
                    }
                }
            }
        });
        tagView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return gestureDetector.onTouchEvent(motionEvent);
            }
        });
        tagView.setOnDragListener(new View.OnDragListener() {
            @Override
            public boolean onDrag(View view, DragEvent dragEvent) {
                final int action = dragEvent.getAction();
                PointF locationInSource = tagView.viewToSourceCoord(dragEvent.getX(), dragEvent.getY());
                PointF tagCenterToLocation = new PointF(
                        locationInSource.x - currentlyEditedTag.getCenterX(),
                        locationInSource.y - currentlyEditedTag.getCenterY()
                );
                double locationAngle = Math.toDegrees(Math.atan2(tagCenterToLocation.y, tagCenterToLocation.x));
                double tagDragAngle;
                double newTagAngle;
                switch (action) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        dragStartingAngle = locationAngle;
                        tagOrientationBeforeDrag = currentlyEditedTag.getOrientation();
                        break;
                    case DragEvent.ACTION_DRAG_LOCATION:
                        tagDragAngle = ((locationAngle - dragStartingAngle) + 360) % 360;
                        newTagAngle = (Math.toDegrees(tagOrientationBeforeDrag) + tagDragAngle) % 360;
                        currentlyEditedTag.setOrientation(Math.toRadians(newTagAngle));
                        break;
                    case DragEvent.ACTION_DROP:
                        tagDragAngle = ((locationAngle - dragStartingAngle) + 360) % 360;
                        newTagAngle = (Math.toDegrees(tagOrientationBeforeDrag) + tagDragAngle) % 360;
                        currentlyEditedTag.setOrientation(Math.toRadians(newTagAngle));
                        break;
                }
                tagView.invalidate();
                return true;
            }
        });

        new GetTagsTask().execute(imageUri);
        tagView.setImage(ImageSource.uri(imageUri));
        setViewMode(ViewMode.TAGGING_MODE);
    }

    @Override
    public void onTagTimePicked(int hour, int minute) {
        if (!tagView.isReady() || viewMode != ViewMode.EDITING_MODE) {
            return;
        }

        MutableDateTime mutableTagDate = currentlyEditedTag.getDate().toMutableDateTime();
        mutableTagDate.setHourOfDay(hour);
        mutableTagDate.setMinuteOfHour(minute);
        DateTime tagDate = mutableTagDate.toDateTime();
        currentlyEditedTag.setDate(tagDate);
        tagTimeTextView.setText(String.format(
                getResources().getString(R.string.tag_time),
                tagDate.getHourOfDay(),
                tagDate.getMinuteOfHour()));
    }

    @Override
    public void onTagDatePicked(int year, int month, int day) {
        if (!tagView.isReady() || viewMode != ViewMode.EDITING_MODE) {
            return;
        }

        MutableDateTime mutableTagDate = currentlyEditedTag.getDate().toMutableDateTime();
        mutableTagDate.setYear(year);
        mutableTagDate.setMonthOfYear(month);
        mutableTagDate.setDayOfMonth(day);
        DateTime tagDate = mutableTagDate.toDateTime();
        currentlyEditedTag.setDate(tagDate);
        tagDateTextView.setText(String.format(
                getResources().getString(R.string.tag_date),
                tagDate.getDayOfMonth(),
                tagDate.monthOfYear().getAsShortText(),
                tagDate.getYear()));
    }

    @Override
    public void onTagDeletionConfirmed() {
        new DatabaseDeleteTask().execute(currentlyEditedTag);
        setViewMode(ViewMode.TAGGING_MODE);
    }

    // inserts a tag with ID 0 into the database,
    // at the specified position, with the specified radius
    private void insertDummyTag(PointF position, float radius) {
        Tag dummyTag = new Tag();
        dummyTag.setEntryId(UUID.randomUUID().toString());
        dummyTag.setBeeId(0);
        dummyTag.setImageName(imageUri.getLastPathSegment());
        dummyTag.setCenterX(position.x);
        dummyTag.setCenterY(position.y);
        dummyTag.setRadius(radius);
        dummyTag.setOrientation(0);
        dummyTag.setDate(new DateTime(new File(imageUri.getPath()).lastModified()));
        dummyTag.setLabel(sharedPreferences.getString("pref_default_label", ""));
        new DatabaseInsertTask().execute(dummyTag);
    }

    // sets the view mode and changes UI accordingly
    private void setViewMode(ViewMode viewMode) {
        this.viewMode = viewMode;
        tagView.setViewMode(viewMode);
        switch (viewMode) {
            case TAGGING_MODE:
                tagButton.setVisibility(View.VISIBLE);
                currentlyEditedTag = null;
                tagInfoLayout.setVisibility(View.INVISIBLE);
                tagView.setPanEnabled(true);
                tagView.setZoomEnabled(true);
                if (tagView.isReady()) {
                    tagView.moveViewBack();
                }
                tagView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_CENTER);
                break;
            case EDITING_MODE:
                // if bee name should be displayed, append it to ID
                String beeNameSuffix;
                if (sharedPreferences.getBoolean("pref_display_bee_name", true)) {
                    beeNameSuffix = String.format(
                            getResources().getString(R.string.bee_name_appended),
                            beeNamer.getBeeName(currentlyEditedTag.getBeeId()));
                } else {
                    beeNameSuffix = "";
                }
                beeIdTextView.setText(String.format(
                        getResources().getString(R.string.bee_id),
                        currentlyEditedTag.getBeeId(),
                        beeNameSuffix));
                DateTime tagDate = currentlyEditedTag.getDate();
                tagDateTextView.setText(String.format(
                        getResources().getString(R.string.tag_date),
                        tagDate.getDayOfMonth(),
                        tagDate.monthOfYear().getAsShortText(),
                        tagDate.getYear()));
                tagTimeTextView.setText(String.format(
                        getResources().getString(R.string.tag_time),
                        tagDate.getHourOfDay(),
                        tagDate.getMinuteOfHour()));
                detectionIdTextView.setText(String.format(
                        getResources().getString(R.string.tag_detection_id),
                        currentlyEditedTag.getEntryId()));
                tagLabelEditText.setText(currentlyEditedTag.getLabel());
                tagNotesEditText.setText(currentlyEditedTag.getNotes());
                tagButton.setVisibility(View.INVISIBLE);
                textInputDoneButton.setVisibility(View.INVISIBLE);
                tagInfoLayout.setVisibility(View.VISIBLE);
                tagView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_OUTSIDE);
                if (tagView.isReady()) {
                    PointF tagCenterInView = new PointF(
                            tagView.getWidth() / 2,
                            (tagView.getHeight() + tagInfoLayout.getHeight()) / 2
                    );
                    int diameter = Math.round(Math.min(
                            tagView.getHeight() - tagInfoLayout.getHeight(),
                            tagView.getWidth()) * 0.95f);
                    tagView.moveViewToTag(
                            currentlyEditedTag,
                            tagCenterInView,
                            diameter);
                }
                tagView.setPanEnabled(false);
                tagView.setZoomEnabled(false);
                break;
        }
    }

    private URL buildUrl() throws JSONException, MalformedURLException, UnsupportedEncodingException {
        JSONArray output;
        Uri serverUri;
        if (sharedPreferences.getBoolean("pref_detect_all_tags", false)) {
            output = new JSONArray(new String[]{"IDs", "Orientations", "LocalizerPositions"});
            serverUri = Uri.parse(sharedPreferences.getString("pref_decoding_server_url", ""))
                    .buildUpon()
                    .appendPath("decode")
                    .appendPath("automatic")
                    .appendQueryParameter("output", URLEncoder.encode(output.toString(), "UTF-8"))
                    .build();
        } else {
            output = new JSONArray(new String[]{"IDs", "Orientations"});
            serverUri = Uri.parse(sharedPreferences.getString("pref_decoding_server_url", ""))
                    .buildUpon()
                    .appendPath("decode")
                    .appendPath("single")
                    .appendQueryParameter("output", URLEncoder.encode(output.toString(), "UTF-8"))
                    .build();
        }
        return new URL(serverUri.toString());
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
