package com.aki.beetag;

import android.app.Activity;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import org.json.JSONArray;
import org.json.JSONException;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.PngWriter;

public class DecodingActivity extends Activity {

    public enum ViewMode {
        TAGGING_MODE, EDITING_MODE
    }
    private ViewMode viewMode;

    private TagView tagView;
    private FloatingActionButton tagButton;
    private FloatingActionButton deleteTagButton;
    private FloatingActionButton saveEditedTagButton;
    private File imageFolder;
    private Uri imageUri;
    private String imageName;

    private TagDatabase database = null;
    private TagDao dao;

    private Tag currentlyEditedTag;
    private double dragStartingAngle;
    private double tagOrientationBeforeDrag;

    private class ServerRequestTask extends AsyncTask<DecodingData, Void, List<Tag>> {
        @Override
        protected List<Tag> doInBackground(DecodingData... decodingData) {
            DecodingData data = decodingData[0];
            BitmapFactory.Options options = new BitmapFactory.Options();
            Bitmap imageBitmap = BitmapFactory.decodeFile(new File(data.imageUri.getPath()).getAbsolutePath(), options);
            int imageWidth = options.outWidth;
            int imageHeight = options.outHeight;

            switch (data.appliedOrientation) {
                case 90:
                    // rotate tagCenter by 90 degrees, counter-clockwise
                    data.tagCenter.set(data.tagCenter.y, imageHeight-data.tagCenter.x);
                    break;
                case 180:
                    // rotate tagCenter by 180 degrees
                    data.tagCenter.set(imageWidth-data.tagCenter.x, imageHeight-data.tagCenter.y);
                    break;
                case 270:
                    // rotate tagCenter by 90 degrees, clockwise
                    data.tagCenter.set(imageWidth-data.tagCenter.y, data.tagCenter.x);
                    break;
            }

            Matrix rotationMatrix = new Matrix();
            rotationMatrix.postRotate(data.appliedOrientation);
            int tagSizeTargetInPx = 50;
            float tagScaleToTarget = tagSizeTargetInPx / data.tagSizeInPx;
            rotationMatrix.postScale(tagScaleToTarget, tagScaleToTarget);

            // increase size of crop square by 30% on each side for padding
            ImageSquare cropSquare = new ImageSquare(data.tagCenter, data.tagSizeInPx * 1.6f);
            Rect imageCropZone = cropSquare.getImageOverlap(imageWidth, imageHeight);
            Bitmap croppedTag = Bitmap.createBitmap(
                    imageBitmap,
                    imageCropZone.left,
                    imageCropZone.top,
                    imageCropZone.right - imageCropZone.left,
                    imageCropZone.bottom - imageCropZone.top,
                    rotationMatrix,
                    // TODO: check results with filter = true
                    false);

            // Intermediate stream that the PNG is written to,
            // to find out the overall data size.
            // After writing, the connection is opened with the
            // appropriate data length.
            // (ChunkedStreamingMode is not supported by the bb_pipeline_api,
            // so we have to use FixedLengthStreamingMode)
            ByteArrayOutputStream pngStream = new ByteArrayOutputStream();
            int cutoutWidth = croppedTag.getWidth();
            int cutoutHeight = croppedTag.getHeight();

            // single channel (grayscale, no alpha) PNG, 8 bit, not indexed
            ImageInfo imgInfo = new ImageInfo(cutoutWidth, cutoutHeight, 8, false, true, false);
            PngWriter pngWriter = new PngWriter(pngStream, imgInfo);
            int[] grayLine = new int[cutoutWidth];
            for (int y = 0; y < cutoutHeight; y++) {
                // write PNG line by line
                for (int x = 0; x < cutoutWidth; x++) {
                    int pixel = croppedTag.getPixel(x, y);
                    int grayValue = (int) (0.2125 * ((pixel >> 16) & 0xff));
                    grayValue += (int) (0.7154 * ((pixel >> 8) & 0xff));
                    grayValue += (int) (0.0721 * (pixel & 0xff));
                    grayLine[x] = grayValue;
                }
                pngWriter.writeRowInt(grayLine);
            }
            pngWriter.end();

            BufferedOutputStream out = null;
            BufferedInputStream in = null;
            HttpURLConnection connection = null;
            List<Tag> tags = new ArrayList<>();

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
                    }
                }
                int tagCount = Math.min(ids.size(), orientations.size());
                for (int i = 0; i < tagCount; i++) {
                    Tag tag = new Tag();
                    tag.setCenterX(data.tagCenter.x);
                    tag.setCenterY(data.tagCenter.y);
                    tag.setRadius(data.tagSizeInPx / 2);
                    tag.setImageName(imageName);
                    tag.setOrientation(orientations.get(i));
                    tag.setBeeId(Tag.bitIdToDecimalId(ids.get(i)));
                    tags.add(tag);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                    if (in != null) {
                        in.close();
                    }
                    if (connection != null) {
                        connection.disconnect();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return tags;
        }

        @Override
        protected void onPostExecute(List<Tag> result) {
            if (result.size() > 1) {
                Toast.makeText(getApplicationContext(), "Found more than one tag!", Toast.LENGTH_LONG).show();
                return;
            } else if (result.isEmpty()) {
                Toast.makeText(getApplicationContext(), "No tag found :(", Toast.LENGTH_LONG).show();
                Tag resultTag = new Tag();
                resultTag.setBeeId(0);
                resultTag.setImageName(imageName);
                PointF tagCenter = tagView.getCenter();
                resultTag.setCenterX(tagCenter.x);
                resultTag.setCenterY(tagCenter.y);
                resultTag.setRadius(tagView.getTagCircleRadius() / tagView.getScale());
                resultTag.setOrientation(0);
                new DatabaseInsertTask().execute(resultTag);
                return;
            }

            new DatabaseInsertTask().execute(result.get(0));
            Toast.makeText(getApplicationContext(), "" + result.get(0).getBeeId(), Toast.LENGTH_LONG).show();
        }
    }

    private class GetTagsTask extends AsyncTask<String, Void, List<Tag>> {
        @Override
        protected List<Tag> doInBackground(String... files) {
            return dao.loadTagsByImage(files[0]);
        }

        @Override
        protected void onPostExecute(List<Tag> tags) {
            tagView.setTagsOnImage(tags);
        }
    }

    private class DatabaseInsertTask extends AsyncTask<Tag, Void, Void> {
        @Override
        protected Void doInBackground(Tag... tags) {
            dao.insertTags(tags[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            new GetTagsTask().execute(imageName);
        }
    }

    private class DatabaseDeleteTask extends AsyncTask<Tag, Void, Void> {
        @Override
        protected Void doInBackground(Tag... tags) {
            dao.deleteTags(tags[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            new GetTagsTask().execute(imageName);
        }
    }

    private class DatabaseUpdateTask extends AsyncTask<Tag, Void, Void> {
        @Override
        protected Void doInBackground(Tag... tags) {
            dao.updateTags(tags[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            new GetTagsTask().execute(imageName);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag);

        Intent intent = getIntent();
        imageUri = intent.getData();
        imageFolder = new File(
                ((Uri) intent.getExtras().get("imageFolder")).getPath()
        );

        // get image name from uri
        if (imageUri.getScheme().equals("file")) {
            imageName = imageUri.getLastPathSegment();
        } else {
            imageName = "unknown";
        }

        RoomDatabase.Builder<TagDatabase> databaseBuilder = Room.databaseBuilder(getApplicationContext(), TagDatabase.class, "beetag-database");
        databaseBuilder.fallbackToDestructiveMigration();
        database = databaseBuilder.build();
        dao = database.getDao();

        tagButton = findViewById(R.id.button_tag);
        tagButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!tagView.isReady()) {
                    return;
                }

                URL serverUrl = null;
                try {
                    serverUrl = buildUrl();
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
                PointF tagCenter = tagView.getCenter();
                float tagSizeInPx = (tagView.getTagCircleRadius() * 2) / tagView.getScale();
                int appliedOrientation = tagView.getAppliedOrientation();

                DecodingData tagData = new DecodingData(
                        serverUrl,
                        imageUri,
                        tagCenter,
                        tagSizeInPx,
                        appliedOrientation);
                new ServerRequestTask().execute(tagData);
            }
        });

        deleteTagButton = findViewById(R.id.button_delete_tag);
        deleteTagButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!tagView.isReady()) {
                    return;
                }

                if (currentlyEditedTag != null) {
                    new DatabaseDeleteTask().execute(currentlyEditedTag);
                    setViewMode(ViewMode.TAGGING_MODE);
                }
            }
        });
        deleteTagButton.setVisibility(View.INVISIBLE);

        saveEditedTagButton = findViewById(R.id.button_save_edited_tag);
        saveEditedTagButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!tagView.isReady()) {
                    return;
                }

                if (currentlyEditedTag != null) {
                    new DatabaseUpdateTask().execute(currentlyEditedTag);
                    Toast.makeText(getApplicationContext(), "" + currentlyEditedTag.getBeeId(), Toast.LENGTH_LONG).show();
                    setViewMode(ViewMode.TAGGING_MODE);
                }
            }
        });
        saveEditedTagButton.setVisibility(View.INVISIBLE);

        tagView = findViewById(R.id.tag_view);
        setViewMode(ViewMode.TAGGING_MODE);
        tagView.setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
        tagView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_CENTER);
        tagView.setMinimumDpi(10);
        tagView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER);

        final GestureDetector gestureDetector = new GestureDetector(getApplicationContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (!tagView.isReady()) {
                    return true;
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
                    Log.d("debug", "toggled bit: " + toggledBitPosition);
                    if (toggledBitPosition != -1) {
                        // invert bit that was tapped
                        id.set(toggledBitPosition, 1 - id.get(toggledBitPosition));
                        currentlyEditedTag.setBeeId(Tag.bitIdToDecimalId(id));
                        Log.d("debug", "ID: " + id);
                        // update view to show changed tag
                        tagView.invalidate();
                    } else {
                        new GetTagsTask().execute(imageName);
                        setViewMode(ViewMode.TAGGING_MODE);
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
                                0
                        );
                    } else {
                        tagView.startDragAndDrop(
                                null,
                                new View.DragShadowBuilder(),
                                null,
                                0
                        );
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

        new GetTagsTask().execute(imageName);
        tagView.setImage(ImageSource.uri(imageUri));
    }

    // sets the view mode and changes UI accordingly
    private void setViewMode(ViewMode viewMode) {
        this.viewMode = viewMode;
        tagView.setViewMode(viewMode);
        if (!tagView.isReady()) {
            return;
        }
        switch (this.viewMode) {
            case TAGGING_MODE:
                tagButton.setVisibility(View.VISIBLE);
                deleteTagButton.setVisibility(View.INVISIBLE);
                saveEditedTagButton.setVisibility(View.INVISIBLE);
                currentlyEditedTag = null;
                tagView.setPanEnabled(true);
                tagView.setZoomEnabled(true);
                tagView.moveViewBack();
                break;
            case EDITING_MODE:
                tagButton.setVisibility(View.INVISIBLE);
                deleteTagButton.setVisibility(View.VISIBLE);
                saveEditedTagButton.setVisibility(View.VISIBLE);
                tagView.moveViewToTag(currentlyEditedTag);
                tagView.setPanEnabled(false);
                tagView.setZoomEnabled(false);
                break;
        }
    }

    private URL buildUrl() throws JSONException, MalformedURLException {
        String address = "http://662a6528.ngrok.io/process";

        JSONArray output = new JSONArray(new String[] {"IDs", "Orientations"});
        HashMap<String, String> params = new HashMap<>();
        params.put("output", output.toString());

        return new URL(address + buildUrlParamsString(params));
    }

    // from a HashMap of key/value pairs, return the URL query string;
    // values are percent-encoded
    private String buildUrlParamsString(HashMap<String, String> params) {
        StringBuilder stringBuilder = new StringBuilder();
        boolean first = true;
        try {
            for (HashMap.Entry<String, String> entry : params.entrySet()) {
                if (!first) {
                    stringBuilder.append("&");
                } else {
                    stringBuilder.append("?");
                    first = false;
                }
                stringBuilder.append(entry.getKey());
                stringBuilder.append("=");
                stringBuilder.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (database != null) {
            database.close();
        }
        dao = null;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        database = Room.databaseBuilder(getApplicationContext(), TagDatabase.class, "beetag-database").build();
        dao = database.getDao();
    }
}
