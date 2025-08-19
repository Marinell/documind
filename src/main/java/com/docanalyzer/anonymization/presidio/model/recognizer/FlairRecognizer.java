package com.docanalyzer.anonymization.presidio.model.recognizer;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class FlairRecognizer extends AdHocRecognizer {

    @JsonProperty("model")
    private String model;

    public FlairRecognizer(String model, List<String> supportedEntities) {
        super(supportedEntities);
        this.model = model;
    }

    //<editor-fold desc="Getters and Setters">
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
    //</editor-fold>
}
