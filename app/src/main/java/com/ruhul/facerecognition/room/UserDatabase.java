package com.ruhul.facerecognition.room;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.ruhul.facerecognition.FaceModel;

@Database(entities = {FaceModel.class}, version = 1, exportSchema = false)
public abstract class UserDatabase extends RoomDatabase {
    public abstract UserDao userDao();

    public static volatile UserDatabase instance;
    public static String Database_Name = "User.db";

    public static synchronized UserDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (UserDatabase.class) {
                if (instance == null) {

                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    UserDatabase.class, Database_Name)
                            .fallbackToDestructiveMigration()
                            .allowMainThreadQueries()
                            .build();

                }
            }

        }
        return instance;
    }
}
