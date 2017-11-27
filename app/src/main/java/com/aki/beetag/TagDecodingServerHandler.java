package com.aki.beetag;

import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;

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

    public void sendRequestForResult(Bitmap croppedTag) {
        try {
            new Thread(new ServerRequestTask(buildUrl(), croppedTag)).start();
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private URL buildUrl() throws JSONException, MalformedURLException {
        String address = "http://f453b557.ngrok.io/process";

        JSONArray output = new JSONArray(new String[] {"IDs"});
        HashMap<String, String> params = new HashMap<>();
        params.put("output", output.toString());

        return new URL(address + buildUrlParamsString(params));
    }

    // from a HashMap of key/value pairs, return the URL query string;
    // values are percent-encoded
    private String buildUrlParamsString(HashMap<String, String> params) {
        StringBuilder stringBuilder = new StringBuilder();
        boolean first = true;
        try {
            for (HashMap.Entry<String, String> entry : params.entrySet()) {
                if (!first) {
                    stringBuilder.append("&");
                } else {
                    stringBuilder.append("?");
                    first = false;
                }
                stringBuilder.append(entry.getKey());
                stringBuilder.append("=");
                stringBuilder.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }
}
