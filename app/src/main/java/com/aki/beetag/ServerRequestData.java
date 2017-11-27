package com.aki.beetag;

import android.net.Uri;

import java.net.URL;

public class ServerRequestData {
    public URL serverUrl;
    public Uri imageUri;
    public int appliedOrientation;
    public ImageSquare tagSquare;

    public ServerRequestData(URL serverUrl, Uri imageUri, int appliedOrientation, ImageSquare tagSquare) {
        this.serverUrl = serverUrl;
        this.imageUri = imageUri;
        this.appliedOrientation = appliedOrientation;
        this.tagSquare = tagSquare;
    }
}
