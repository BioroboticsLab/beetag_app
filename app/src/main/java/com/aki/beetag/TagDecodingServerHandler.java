package com.aki.beetag;

import android.net.Uri;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;

public class TagDecodingServerHandler {

    private void SendRequestForResult(URL url, Uri imageUri) {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/octet-stream");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // from a HashMap of key/value pairs, return the percent encoded URL query string
    private String getUrlParamsString(HashMap<String, String> params) {
        StringBuilder stringBuilder = new StringBuilder();
        boolean first = true;
        for (HashMap.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                stringBuilder.append("&");
            } else {
                stringBuilder.append("?");
                first = false;
            }
            stringBuilder.append(entry.getKey());
            stringBuilder.append("=");
            stringBuilder.append(entry.getValue());
        }
        try {
            return URLEncoder.encode(stringBuilder.toString(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } finally {
            return stringBuilder.toString();
        }
    }
}
