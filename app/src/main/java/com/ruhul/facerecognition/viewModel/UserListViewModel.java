package com.ruhul.facerecognition.viewModel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.ruhul.facerecognition.FaceModel;
import com.ruhul.facerecognition.repository.UserListRepository;

import java.util.HashMap;

public class UserListViewModel extends AndroidViewModel {

    public UserListRepository userListRepository;


    public UserListViewModel(@NonNull Application application) {
        super(application);

        userListRepository = UserListRepository.getInstance(application);
    }

    public void insert(FaceModel faceModel) {
        userListRepository.insert(faceModel);
    }

    public LiveData<HashMap<String, FaceModel>> getUserList() {
        return userListRepository.getUserList();
    }

}
