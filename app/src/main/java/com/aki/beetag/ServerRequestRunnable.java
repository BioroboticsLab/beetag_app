package com.aki.beetag;

import android.graphics.Bitmap;
import android.util.Log;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setFixedLengthStreamingMode(50);
            connection.setDoOutput(true);
            //connection.setChunkedStreamingMode(0);
            int cutoutWidth = bitmap.getWidth();
            int cutoutHeight = bitmap.getHeight();
            BufferedOutputStream out = null;
            InputStream in = null;
            try {
                out = new BufferedOutputStream(connection.getOutputStream());
                /*
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
                */
                for (int i = 0; i < 50; i++) {
                    out.write(i);
                    Log.d("cameradebug", "wrote " + i);
                }
                out.flush();
                in = new BufferedInputStream(connection.getInputStream());
                //Log.d("cameradebug", "read(): " + in.read());
                Log.d("cameradebug", "available(): " + in.available());
                MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(in);
                int mapSize = unpacker.unpackMapHeader();
                for (int i = 0; i < mapSize; i++) {
                    String key = unpacker.unpackString();
                    switch (key) {
                        case "IDs":
                            int idsListLength = unpacker.unpackArrayHeader();
                            ArrayList<ArrayList<Integer>> idList = new ArrayList<>();
                            for (int j = 0; j < idsListLength; j++) {
                                ArrayList<Integer> id = new ArrayList<>();
                                int idLength = unpacker.unpackArrayHeader();
                                for (int k = 0; k < idLength; k++) {
                                    id.add(unpacker.unpackInt());
                                }
                                idList.add(id);
                            }
                            Log.d("cameradebug", "IDs: " + idList);
                            break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                out.close();
                connection.disconnect();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
