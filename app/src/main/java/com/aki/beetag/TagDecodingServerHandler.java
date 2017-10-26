package com.aki.beetag;

import android.graphics.Bitmap;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;

public class TagDecodingServerHandler {

    private void SendRequestForResult(URL url, Bitmap image) {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(0);
            OutputStream out = new BufferedOutputStream(connection.getOutputStream());
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();

            InputStream in = new BufferedInputStream(connection.getInputStream());
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            while ((bytesRead = in.read(buffer, 0, bufferSize)) != -1) {
                data.write(buffer, 0, bytesRead);
            }
            data.flush();
            byte[] response = data.toByteArray();


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private URL buildUrl() throws JSONException, MalformedURLException {
        String address = "http://tonic.imp.fu-berlin.de:10000/process";

        JSONArray output = new JSONArray(new String[] {"IDs"});
        HashMap<String, String> params = new HashMap<>();
        params.put("output", output.toString());

        return new URL(address + buildUrlParamsString(params));
    }

    // from a HashMap of key/value pairs, return the (percent encoded) URL query string
    private String buildUrlParamsString(HashMap<String, String> params) {
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
