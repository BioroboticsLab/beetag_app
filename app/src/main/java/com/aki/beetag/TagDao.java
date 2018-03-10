package com.aki.beetag;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface TagDao {
    @Insert
    void insertTags(Tag... tags);

    @Update
    void updateTags(Tag... tags);

    @Delete
    void deleteTags(Tag... tags);

    @Query("DELETE FROM tag WHERE imageName=:imageName")
    void deleteAllTagsOnImage(String imageName);

    @Query("SELECT * FROM tag WHERE imageName=:imageName")
    List<Tag> loadTagsByImage(String imageName);

    @Query("SELECT COUNT(*) FROM TAG WHERE imageName=:imageName")
    int getTagCount(String imageName);
}
