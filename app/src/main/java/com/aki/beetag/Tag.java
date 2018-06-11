package com.aki.beetag;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.TypeConverters;
import android.support.annotation.NonNull;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Collections;

@Entity(tableName = "tag")
@TypeConverters({DateTimeConverter.class})
public class Tag {
    @NonNull
    @PrimaryKey
    private String entryId;
    private DateTime date;
    private int beeId;
    private String beeName;
    private String label;
    private String imageName;
    private String notes;
    private float centerX;
    private float centerY;
    private float radius;
    private double orientation;

    /*
    Tag(
            DateTime date,
            int beeId,
            String beeName,
            String label,
            String imageName,
            String notes,
            float centerX,
            float centerY,
            float radius,
            double orientation
    ) {
        this.date = date;
        this.beeId = beeId;
        this.beeName = beeName;
        this.label = label;
        this.imageName = imageName;
        this.notes = notes;
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
        this.orientation = orientation;
    }
    */

    /* Getters and setters */

    public String getEntryId() {
        return entryId;
    }

    public void setEntryId(String entryId) {
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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    // Converts ID in bit array format to decimal representation format used by Fernando Wario.
    // Description of the format conversion:
    // If the tag is oriented with white facing north, the first 11 bits are read clockwise
    // from the leftmost bit on the northern half (9 o' clock position), most significant
    // bit first, resulting in a decimal number. If the last bit (8 o' clock position) acts as
    // a parity bit, i.e. if it indicates an odd number of set bits in the first 11 bits, then
    // the decimal number is the bee ID. Otherwise, the bee ID equals the decimal number + 2048.
    public static int bitIdToDecimalId(ArrayList<Integer> bitId) {
        // rotate the id by 3 so we start at "9 o' clock" instead of "12 o' clock"
        ArrayList<Integer> rotatedId = new ArrayList<>(bitId);
        Collections.rotate(rotatedId, 3);
        int decimalId = 0;
        int setBitsCount = 0;
        for (int i = 0; i < 11; i++) {
            int bit = rotatedId.get(i);
            // count set bits for parity bit checking
            setBitsCount += bit;
            // convert bit list to decimal (most significant bit first)
            decimalId = (decimalId << 1) | bit;
        }
        int parityBit = rotatedId.get(11);
        // check if parity bit matches
        if ((setBitsCount % 2) != parityBit) {
            decimalId += 2048;
        }
        return decimalId;
    }

    // Converts id in decimal representation format used by Fernando Wario to bit array format.
    // This reverses the format conversion in bitIdToDecimalId()
    public static ArrayList<Integer> decimalIdToBitId(int decimalId) {
        boolean parityNeedsToMatch = decimalId < 2048;
        if (!parityNeedsToMatch) {
            decimalId -= 2048;
        }
        ArrayList<Integer> bitId = new ArrayList<>(12);
        int setBitsCount = 0;
        for (int i = 10; i >= 0; i--) {
            // read bits from most to least significant
            int bit = (decimalId >> i) & 1;
            bitId.add(bit);
            // count set bits
            setBitsCount += bit;
        }
        // set "parity bit"
        bitId.add(parityNeedsToMatch ? (setBitsCount % 2) : (1 - (setBitsCount % 2)));
        // rotate the id by -3 so we start at "12 o' clock" instead of "9 o' clock"
        Collections.rotate(bitId, -3);
        return bitId;
    }
}
