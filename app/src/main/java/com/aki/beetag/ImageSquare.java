package com.aki.beetag;


import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;

public class ImageSquare {

    private Point upperLeftCorner;
    private int size;

    public ImageSquare() {
        upperLeftCorner = new Point(0, 0);
        size = 0;
    }

    public ImageSquare(Point upperLeftCorner, int size) {
        this.upperLeftCorner = upperLeftCorner;
        this.size = size;
    }

    // returns an ImageSquare with dimensions that match the given shape as closely as possible
    public ImageSquare(PointF center, float size) {
        upperLeftCorner = new Point(Math.round(center.x - size/2), Math.round(center.y - size/2));
        this.size = Math.round(size);
    }

    // returns a Rect matching the part of the square that
    // overlaps an image with the given width and height
    public Rect getImageOverlap(int imageWidth, int imageHeight) {
        int left = (upperLeftCorner.x < 0) ? 0 : Math.min(imageWidth, upperLeftCorner.x);
        int top = (upperLeftCorner.y < 0) ? 0 : Math.min(imageHeight, upperLeftCorner.y);
        int right = (upperLeftCorner.x + size < 0) ? 0 : Math.min(imageWidth, upperLeftCorner.x + size);
        int bottom = (upperLeftCorner.y + size < 0) ? 0 : Math.min(imageHeight, upperLeftCorner.y + size);
        return new Rect(left, top, right, bottom);
    }

    public Point getUpperLeftCorner() {
        return upperLeftCorner;
    }

    public void setUpperLeftCorner(Point p) {
        upperLeftCorner = p;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int s) {
        size = s;
    }
}
