package com.aki.beetag;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.TypeConverters;

import org.joda.time.DateTime;

@Entity(tableName = "tag")
@TypeConverters({DateTimeConverter.class})
public class Tag {
    @PrimaryKey//(autoGenerate = true)
    private int entryId;
    private DateTime date;
    private int beeId;
    private String beeName;
    private String label;
    private String imageName;
    private String imageNameSingle;
    private float centerX;
    private float centerY;
    private float radius;
    private double orientation;

    /* Getters and setters */

    public int getEntryId() {
        return entryId;
    }

    public void setEntryId(int entryId) {
        this.entryId = entryId;
    }

    public DateTime getDate() {
        return date;
    }

    public void setDate(DateTime date) {
        this.date = date;
    }

    public int getBeeId() {
        return beeId;
    }

    public void setBeeId(int beeId) {
        this.beeId = beeId;
    }

    public String getBeeName() {
        return beeName;
    }

    public void setBeeName(String beeName) {
        this.beeName = beeName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public String getImageNameSingle() {
        return imageNameSingle;
    }

    public void setImageNameSingle(String imageNameSingle) {
        this.imageNameSingle = imageNameSingle;
    }

    public float getCenterX() {
        return centerX;
    }

    public void setCenterX(float centerX) {
        this.centerX = centerX;
    }

    public float getCenterY() {
        return centerY;
    }

    public void setCenterY(float centerY) {
        this.centerY = centerY;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    public double getOrientation() {
        return orientation;
    }

    public void setOrientation(double orientation) {
        this.orientation = orientation;
    }
}
