package com.docanalyzer.anonymization.presidio.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RecognizerResult {

    @JsonProperty("entity_type")
    private String entityType;
    private int start;
    private int end;
    private float score;

    //<editor-fold desc="Getters and Setters">
    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public float getScore() {
        return score;
    }

    public void setScore(float score) {
        this.score = score;
    }
    //</editor-fold>
}
