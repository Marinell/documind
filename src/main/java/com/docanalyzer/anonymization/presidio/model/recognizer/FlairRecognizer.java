package com.docanalyzer.anonymization.presidio.model.recognizer;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class FlairRecognizer extends AdHocRecognizer {

    @JsonProperty("name")
    private String name;

    public FlairRecognizer(String name, List<String> supportedEntities) {
        super(supportedEntities);
        this.name = name;
    }

    //<editor-fold desc="Getters and Setters">
    public String getModel() {
        return name;
    }

    public void setModel(String model) {
        this.name = model;
    }
    //</editor-fold>
}
