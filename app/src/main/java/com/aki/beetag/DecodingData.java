package com.aki.beetag;

import android.graphics.PointF;
import android.net.Uri;

import java.net.URL;


public class DecodingData {

    public static enum DetectionMode {
        SINGLE, AUTOMATIC
    }

    // detection mode used (single decoding of a tag, or automatic detection of all tags)
    public DetectionMode mode;
    // decoding server URL
    public URL serverUrl;
    public Uri imageUri;
    // center of the decoded tag in the image (only applies in single mode)
    public PointF tagCenter;
    // size of the marked tag in pixels
    public float tagSizeInPx;
    // rotation that was applied to the image before displaying it on screen (based on EXIF data)
    public int appliedOrientation;

    public DecodingData(DetectionMode mode, URL serverUrl, Uri imageUri, PointF tagCenter, float tagSizeInPx, int appliedOrientation) {
        this.mode = mode;
        this.serverUrl = serverUrl;
        this.imageUri = imageUri;
        this.tagCenter = tagCenter;
        this.tagSizeInPx = tagSizeInPx;
        this.appliedOrientation = appliedOrientation;
    }
}
