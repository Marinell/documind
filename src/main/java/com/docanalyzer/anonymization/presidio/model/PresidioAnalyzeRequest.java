package com.docanalyzer.anonymization.presidio.model;

public class PresidioAnalyzeRequest {

    private String text;
    private String language;

    public PresidioAnalyzeRequest(String text, String language) {
        this.text = text;
        this.language = language;
    }

    //<editor-fold desc="Getters and Setters">
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
    //</editor-fold>
}
