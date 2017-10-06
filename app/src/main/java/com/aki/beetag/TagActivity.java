package com.aki.beetag;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

public class TagActivity extends Activity {

    private SubsamplingScaleImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag);

        Intent intent = getIntent();
        Uri imageUri = intent.getData();

        imageView = findViewById(R.id.imageView);
        imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_CENTER);
        imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER);
        imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
        imageView.setImage(ImageSource.uri(imageUri));

    }
}
