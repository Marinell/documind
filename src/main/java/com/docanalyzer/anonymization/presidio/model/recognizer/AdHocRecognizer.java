package com.docanalyzer.anonymization.presidio.model.recognizer;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AdHocRecognizer {

    @JsonProperty("supported_entities")
    private List<String> supportedEntities;

    public AdHocRecognizer(List<String> supportedEntities) {
        this.supportedEntities = supportedEntities;
    }

    //<editor-fold desc="Getters and Setters">
    public List<String> getSupportedEntities() {
        return supportedEntities;
    }

    public void setSupportedEntities(List<String> supportedEntities) {
        this.supportedEntities = supportedEntities;
    }
    //</editor-fold>
}
