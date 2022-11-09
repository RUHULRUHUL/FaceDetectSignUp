package com.ruhul.facerecognition.room;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.ruhul.facerecognition.FaceModel;

import java.util.HashMap;
import java.util.List;

@Dao
public interface UserDao {

    @Insert
    void insertUserFace(FaceModel faceModel);

    @Query("Select * From User")
    LiveData<HashMap<String, FaceModel>> getUserList();
}
