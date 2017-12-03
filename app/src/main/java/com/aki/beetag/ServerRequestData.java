package com.aki.beetag;

import android.graphics.PointF;
import android.net.Uri;

import java.net.URL;


public class ServerRequestData {
    public URL serverUrl;
    public Uri imageUri;
    public PointF tagCenter;
    public float tagSizeInPx;
    public int appliedOrientation;

    public ServerRequestData(URL serverUrl, Uri imageUri, PointF tagCenter, float tagSizeInPx, int appliedOrientation) {
        this.serverUrl = serverUrl;
        this.imageUri = imageUri;
        this.tagCenter = tagCenter;
        this.tagSizeInPx = tagSizeInPx;
        this.appliedOrientation = appliedOrientation;
    }
}
