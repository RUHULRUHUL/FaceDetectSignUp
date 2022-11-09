package com.ruhul.facerecognition.repository;

import android.content.Context;
import android.os.AsyncTask;

import androidx.lifecycle.LiveData;

import com.ruhul.facerecognition.FaceModel;
import com.ruhul.facerecognition.room.UserDao;
import com.ruhul.facerecognition.room.UserDatabase;

import java.util.HashMap;


public class UserListRepository {

    public static UserListRepository userListRepository;
    public static UserDatabase userDatabase;
    public static UserDao userDao;
    private static LiveData<HashMap<String, FaceModel>> userListLiveData;


    public static UserListRepository getInstance(Context context) {
        if (userListRepository == null) {
            userListRepository = new UserListRepository();
        }

        userDatabase = UserDatabase.getInstance(context);
        userListLiveData = userDatabase.userDao().getUserList();

        return userListRepository;
    }

    public void insert(FaceModel faceModel) {
        new InsertNoteAsyncTask().execute(faceModel);
    }

    public LiveData<HashMap<String, FaceModel>> getUserList() {
        return userListLiveData;
    }

    private static class InsertNoteAsyncTask extends AsyncTask<FaceModel, Void, Void> {
        @Override
        protected Void doInBackground(FaceModel... faceModel) {
            userDatabase.userDao().insertUserFace(faceModel[0]);
            return null;
        }
    }


}
