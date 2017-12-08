package com.aki.beetag;

import android.arch.persistence.room.TypeConverter;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;


public class DateTimeConverter {
    private static final DateTimeFormatter format = ISODateTimeFormat.dateTimeNoMillis();

    @TypeConverter
    public static String toString(DateTime dateTime) {
        return format.print(dateTime);
    }

    @TypeConverter
    public static DateTime toDateTime(String string) {
        return new DateTime(string);
    }
}
