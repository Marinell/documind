package com.docanalyzer.anonymization.presidio.model;

import com.docanalyzer.anonymization.presidio.model.recognizer.AdHocRecognizer;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class PresidioAnalyzeRequest {

    private String text;
    private String language;
    /*@JsonProperty("ad_hoc_recognizers")
    private List<AdHocRecognizer> adHocRecognizers;*/

    public PresidioAnalyzeRequest(String text, String language) {
        this.text = text;
        this.language = language;
    }

    public PresidioAnalyzeRequest(String text, String language, List<AdHocRecognizer> adHocRecognizers) {
        this.text = text;
        this.language = language;
        // this.adHocRecognizers = adHocRecognizers;
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

    /*public List<AdHocRecognizer> getAdHocRecognizers() {
        return adHocRecognizers;
    }

    public void setAdHocRecognizers(List<AdHocRecognizer> adHocRecognizers) {
        this.adHocRecognizers = adHocRecognizers;
    }*/
    //</editor-fold>
}
