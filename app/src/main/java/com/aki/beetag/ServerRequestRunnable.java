package com.aki.beetag;

import android.graphics.Bitmap;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.PngWriter;

public class ServerRequestRunnable implements Runnable {

    private URL url;
    private Bitmap bitmap;

    public ServerRequestRunnable(URL address, Bitmap image) {
        url = address;
        bitmap = image;
    }

    @Override
    public void run() {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setDoOutput(true);
            connection.setChunkedStreamingMode(0);
            int cutoutWidth = bitmap.getWidth();
            int cutoutHeight = bitmap.getHeight();
            BufferedOutputStream out = null;
            try {
                out = new BufferedOutputStream(connection.getOutputStream());
                ImageInfo imgInfo = new ImageInfo(bitmap.getWidth(), bitmap.getHeight(), 8, false, true, false);
                PngWriter pngWriter = new PngWriter(out, imgInfo);
                int[] grayLine = new int[cutoutWidth];
                for (int y = 0; y < cutoutHeight; y++) {
                    for (int x = 0; x < cutoutWidth; x++) {
                        int pixel = bitmap.getPixel(x, y);
                        int grayValue = (int) (0.2125 * ((pixel >> 16) & 0xff));
                        grayValue += (int) (0.7154 * ((pixel >> 8) & 0xff));
                        grayValue += (int) (0.0721 * (pixel & 0xff));
                        grayLine[x] = grayValue;
                    }
                    pngWriter.writeRowInt(grayLine);
                }
                pngWriter.end();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                out.flush();
                out.close();
            }
            InputStream in = new BufferedInputStream(connection.getInputStream());
            Log.d("cameradebug", "" + in.available());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
