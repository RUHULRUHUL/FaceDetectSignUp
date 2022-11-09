package com.ruhul.facerecognition;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
@Entity(tableName = "User")
public class FaceModel {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "title")
    private String name;

    private SimilarityClassifier.Recognition result;

    public FaceModel(String toString, SimilarityClassifier.Recognition result) {
        this.name = toString;
        this.result = result;
    }
}
