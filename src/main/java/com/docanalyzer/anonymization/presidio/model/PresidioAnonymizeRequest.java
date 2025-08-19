package com.docanalyzer.anonymization.presidio.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class PresidioAnonymizeRequest {

    private String text;
    @JsonProperty("analyzer_results")
    private List<RecognizerResult> analyzerResults;
    private Map<String, AnonymizerConfig> anonymizers;

    public PresidioAnonymizeRequest(String text, List<RecognizerResult> analyzerResults, Map<String, AnonymizerConfig> anonymizers) {
        this.text = text;
        this.analyzerResults = analyzerResults;
        this.anonymizers = anonymizers;
    }

    //<editor-fold desc="Getters and Setters">
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<RecognizerResult> getAnalyzerResults() {
        return analyzerResults;
    }

    public void setAnalyzerResults(List<RecognizerResult> analyzerResults) {
        this.analyzerResults = analyzerResults;
    }

    public Map<String, AnonymizerConfig> getAnonymizers() {
        return anonymizers;
    }

    public void setAnonymizers(Map<String, AnonymizerConfig> anonymizers) {
        this.anonymizers = anonymizers;
    }
    //</editor-fold>
}
