package com.docanalyzer.anonymization.presidio.model;

public class AddRecognizerRequest {
    private String path;

    public AddRecognizerRequest(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
