package com.aki.beetag;

import android.graphics.Bitmap;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

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
import java.util.ArrayList;
import java.util.HashMap;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.PngWriter;

public class TagDecodingServerHandler {

    private HttpURLConnection connection;
    private URL url;

    public TagDecodingServerHandler() {
        try {
            url = buildUrl();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public OutputStream getOutputStream() {
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(0);
            connection.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void sendRequestForResult(Bitmap croppedTag) {
        try {
            new Thread(new ServerRequestRunnable(buildUrl(), croppedTag)).start();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        /*
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int bytesRead;
        while ((bytesRead = in.read(buffer, 0, bufferSize)) != -1) {
            data.write(buffer, 0, bytesRead);
        }
        data.flush();
        byte[] response = data.toByteArray();
        */

        /*
        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(in);
        int mapSize = unpacker.unpackMapHeader();
        for (int i = 0; i < mapSize; i++) {
            String key = unpacker.unpackString();
            switch (key) {
                case "IDs":
                    int idsLength = unpacker.unpackArrayHeader();
                    ArrayList<ArrayList<Integer>> idlist = new ArrayList<>();
                    for (int j = 0; j < idsLength; j++) {
                        ArrayList<Integer> id = new ArrayList<>();
                        int idLength = unpacker.unpackArrayHeader();
                        // TODO
                    }
                    break;
            }
        }
        */
    }

    private URL buildUrl() throws JSONException, MalformedURLException {
        String address = "http://180dd7e0.ngrok.io/process";

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
