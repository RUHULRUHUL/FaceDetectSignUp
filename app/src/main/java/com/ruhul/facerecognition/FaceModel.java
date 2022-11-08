package com.ruhul.facerecognition;

public class FaceModel {
    private String name;
    private SimilarityClassifier.Recognition result;

    public FaceModel(String toString, SimilarityClassifier.Recognition result) {
        this.name = toString;
        this.result = result;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SimilarityClassifier.Recognition getResult() {
        return result;
    }

    public void setResult(SimilarityClassifier.Recognition result) {
        this.result = result;
    }
}
