package com.aki.beetag;

import android.app.Activity;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
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

    private TagView tagView;
    private ImageButton tagButton;
    private File imageFolder;
    private Uri imageUri;
    private String imageName;

    private TagDatabase database = null;
    private TagDao dao;

    private class ServerRequestTask extends AsyncTask<ServerRequestData, Void, ArrayList<ArrayList<Double>>> {

        @Override
        protected ArrayList<ArrayList<Double>> doInBackground(ServerRequestData... serverRequestData) {
            ServerRequestData data = serverRequestData[0];
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
                for (int i = 0; i < mapSize; i++) {
                    String key = unpacker.unpackString();
                    switch (key) {
                        case "IDs":
                            int idsListLength = unpacker.unpackArrayHeader();
                            ArrayList<ArrayList<Double>> idList = new ArrayList<>();
                            for (int j = 0; j < idsListLength; j++) {
                                ArrayList<Double> id = new ArrayList<>();
                                int idLength = unpacker.unpackArrayHeader();
                                for (int k = 0; k < idLength; k++) {
                                    id.add(unpacker.unpackDouble());
                                }
                                idList.add(id);
                            }
                            return idList;
                    }
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
            return new ArrayList<>();
        }

        @Override
        protected void onPostExecute(ArrayList<ArrayList<Double>> result) {
            /*
            if (result.size() > 1) {
                Toast.makeText(getApplicationContext(), "More than one ID returned!", Toast.LENGTH_LONG).show();
                return;
            } else if (result.isEmpty()) {
                Toast.makeText(getApplicationContext(), "No tag found :(", Toast.LENGTH_LONG).show();
                return;
            }
            */
            ArrayList<Integer> id = new ArrayList<>();
            /*
            for (double detection : result.get(0)) {
                id.add((int) Math.round(detection));
            }
            Toast.makeText(getApplicationContext(), id.toString(), Toast.LENGTH_LONG).show();
            */
            Tag resultTag = new Tag();
            resultTag.setEntryId((int) Math.round(Math.random()*10000));
            resultTag.setBeeId(1337);
            resultTag.setImageName(imageName);
            PointF tagCenter = tagView.getCenter();
            resultTag.setCenterX(tagCenter.x);
            resultTag.setCenterY(tagCenter.y);
            resultTag.setRadius(tagView.getTagCircleRadius() / tagView.getScale());
            new DatabaseInsertTask().execute(resultTag);
        }
    }

    private class DatabaseQueryTask extends AsyncTask<String, Void, List<Tag>> {

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
            new DatabaseQueryTask().execute(imageName);
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

                ServerRequestData tagData = new ServerRequestData(
                        serverUrl,
                        imageUri,
                        tagCenter,
                        tagSizeInPx,
                        appliedOrientation);
                new ServerRequestTask().execute(tagData);
            }
        });

        tagView = findViewById(R.id.tag_view);
        tagView.setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
        tagView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_CENTER);
        tagView.setMinimumDpi(10);
        tagView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER);
        new DatabaseQueryTask().execute(imageName);
        tagView.setImage(ImageSource.uri(imageUri));
    }

    private URL buildUrl() throws JSONException, MalformedURLException {
        String address = "http://4c6c1ce5.ngrok.io/process";

        JSONArray output = new JSONArray(new String[] {"IDs"});
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
