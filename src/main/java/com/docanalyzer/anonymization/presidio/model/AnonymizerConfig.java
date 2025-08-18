package com.docanalyzer.anonymization.presidio.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AnonymizerConfig {

    private String type;
    @JsonProperty("new_value")
    private String newValue;

    public AnonymizerConfig(String type, String newValue) {
        this.type = type;
        this.newValue = newValue;
    }

    //<editor-fold desc="Getters and Setters">
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }
    //</editor-fold>
}
