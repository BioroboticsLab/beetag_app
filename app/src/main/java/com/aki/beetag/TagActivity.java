package com.aki.beetag;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TagActivity extends Activity {

    private TagView tagView;
    private ImageButton tagButton;
    private File imageFolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag);

        Intent intent = getIntent();
        final Uri imageUri = intent.getData();
        imageFolder = new File(
                ((Uri) intent.getExtras().get("imageFolder")).getPath()
        );

        tagButton = findViewById(R.id.button_tag);
        tagButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!tagView.isReady()) {
                    return;
                }

                PointF tagCenter = tagView.getCenter();
                float zoomScale = tagView.getScale();
                int cropCircleScreenSize = tagView.getTagCircleRadius() * 2;

                BitmapFactory.Options options = new BitmapFactory.Options();
                //options.inJustDecodeBounds = true;
                Bitmap imageBitmap = BitmapFactory.decodeFile(new File(imageUri.getPath()).getAbsolutePath(), options);

                int imageWidth = options.outWidth;
                int imageHeight = options.outHeight;
                // number of degrees by which the image was rotated, clockwise
                int imageOrientation = tagView.getAppliedOrientation();
                if (imageOrientation == 90) {
                    // rotate tagCenter by 90 degrees, counter-clockwise
                    tagCenter = new PointF(tagCenter.y, imageHeight-tagCenter.x);
                } else if (imageOrientation == 180) {
                    // rotate tagCenter by 180 degrees
                    tagCenter = new PointF(imageWidth-tagCenter.x, imageHeight-tagCenter.y);
                } else if (imageOrientation == 270) {
                    // rotate tagCenter by 90 degrees, clockwise
                    tagCenter = new PointF(imageWidth-tagCenter.y, tagCenter.x);
                }

                Matrix rotationMatrix = new Matrix();
                rotationMatrix.postRotate(imageOrientation);
                float tagSizeInPx = cropCircleScreenSize / zoomScale;
                int tagSizeTargetInPx = 50;
                float tagScaleToTarget = tagSizeTargetInPx / tagSizeInPx;
                rotationMatrix.postScale(tagScaleToTarget, tagScaleToTarget);

                // increase size of crop square by 30% on each side for padding
                ImageSquare cropSquare = new ImageSquare(tagCenter, tagSizeInPx * 1.6f);
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

                FileOutputStream out = null;
                try {
                    out = new FileOutputStream(createImageFile());
                    croppedTag.compress(Bitmap.CompressFormat.PNG, 100, out);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        tagView = findViewById(R.id.tag_view);
        tagView.setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
        tagView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_CENTER);
        tagView.setMinimumDpi(10);
        tagView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER);
        tagView.setImage(ImageSource.uri(imageUri));
    }

    private File createImageFile() throws IOException {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        File imgFile = File.createTempFile("cropped_" + timestamp + "_", ".png", imageFolder);
        return imgFile;
    }


}
