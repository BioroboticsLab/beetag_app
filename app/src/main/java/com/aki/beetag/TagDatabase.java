package com.aki.beetag;

import android.arch.persistence.room.Database;
import android.arch.persistence.room.RoomDatabase;

@Database(entities = {Tag.class}, version = 1)
public abstract class TagDatabase extends RoomDatabase {
    public abstract TagDao getDao();
}
