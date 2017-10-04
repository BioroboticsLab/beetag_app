package com.aki.beetag;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.github.chrisbanes.photoview.PhotoView;

public class TagActivity extends Activity {

    //private PhotoView photoView;
    private SubsamplingScaleImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag);

        Intent intent = getIntent();
        Uri imageUri = intent.getData();

        //photoView = findViewById(R.id.photo_view);
        //photoView.setImageURI(imageUri);

        imageView = findViewById(R.id.imageView);
        imageView.setImage(ImageSource.uri(imageUri));
    }
}
