package com.aki.beetag;


import android.graphics.Point;
import android.graphics.PointF;

public class ImageSquare {

    Point upperLeftCorner;
    int size;

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
        upperLeftCorner = new Point(Math.round(center.x), Math.round(center.y));
        this.size = Math.round(size);
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
