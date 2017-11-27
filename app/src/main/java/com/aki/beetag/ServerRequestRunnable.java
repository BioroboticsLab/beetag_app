package com.aki.beetag;

import android.graphics.Bitmap;
import android.util.Log;
import android.widget.Toast;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

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
        // Intermediate stream that the PNG is written to.
        // After writing, the connection is opened with the
        // appropriate data length.
        // (ChunkedStreamingMode is not supported by the bb_pipeline_api,
        // so we have to use FixedLengthStreamingMode)
        ByteArrayOutputStream pngStream = new ByteArrayOutputStream();
        int cutoutWidth = bitmap.getWidth();
        int cutoutHeight = bitmap.getHeight();

        // single channel (grayscale, no alpha) PNG, 8 bit, not indexed
        ImageInfo imgInfo = new ImageInfo(cutoutWidth, cutoutHeight, 8, false, true, false);
        PngWriter pngWriter = new PngWriter(pngStream, imgInfo);
        int[] grayLine = new int[cutoutWidth];
        for (int y = 0; y < cutoutHeight; y++) {
            // write PNG line by line
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

        BufferedOutputStream out = null;
        InputStream in = null;
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            // ChunkedStreamingMode is not supported by the bb_pipeline_api, so use fixed length
            connection.setFixedLengthStreamingMode(pngStream.size());
            connection.setDoOutput(true);
            connection.setDoInput(true);

            out = new BufferedOutputStream(connection.getOutputStream());
            pngStream.writeTo(out);
            out.flush();
            in = new BufferedInputStream(connection.getInputStream());
            MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(in);
            int mapSize = unpacker.unpackMapHeader();
            for (int i = 0; i < mapSize; i++) {
                String key = unpacker.unpackString();
                switch (key) {
                    case "IDs":
                        int idsListLength = unpacker.unpackArrayHeader();
                        ArrayList<ArrayList<Double>> idList = new ArrayList<>();
                        for (int j = 0; j < idsListLength; j++) {
                            ArrayList<Double> id = new ArrayList<>();
                            int idLength = unpacker.unpackArrayHeader();
                            for (int k = 0; k < idLength; k++) {
                                id.add(unpacker.unpackDouble());
                            }
                            idList.add(id);
                        }
                        if (idList.isEmpty()) {
                            Log.d("cameradebug", "No bee tags :(");
                        } else {
                            Log.d("cameradebug", "IDs: " + idList);
                        }
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                out.close();
                connection.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
