package com.aki.beetag;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

public class TagActivity extends Activity {

    private SubsamplingScaleImageView imageView;
    private ImageButton tagButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag);

        tagButton = findViewById(R.id.button_tag);
        tagButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PointF tagCenter = imageView.getCenter();
                float tagScale = imageView.getScale();
                Toast.makeText(getApplicationContext(), "Center: " + tagCenter.x + "; " + tagCenter.y + "\nScale: " + tagScale, Toast.LENGTH_SHORT).show();
            }
        });

        Intent intent = getIntent();
        Uri imageUri = intent.getData();

        imageView = findViewById(R.id.imageView);
        imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
        imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_CENTER);
        imageView.setMinimumDpi(10);
        imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER);
        imageView.setImage(ImageSource.uri(imageUri));
    }


}
