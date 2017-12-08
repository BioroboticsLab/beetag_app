package com.aki.beetag;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

@Dao
public interface TagDao {
    @Insert
    public void insertTags(Tag... tags);

    @Update
    public void updateTags(Tag... tags);

    @Delete
    public void deleteTags(Tag... tags);

    @Query("SELECT * FROM tag WHERE imageName=:imageName")
    public Tag[] loadTagsByImage(String imageName);
}
