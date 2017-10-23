package com.aki.beetag;

import android.net.Uri;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

public class DetectionServerHandler {

    private void SendRequestForResult(URL url, Uri imageUri) {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private String getUrlParamsString(Map<String, String> params) {
        return null;
    }
}
