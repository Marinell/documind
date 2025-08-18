package com.docanalyzer.anonymization.deepseek;

public class DeepseekRequest {

    private String model = "deepseek-r1:1.5b"; // or whatever model is appropriate
    private String prompt;
    private boolean stream = false;

    public DeepseekRequest(String prompt) {
        this.prompt = prompt;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }
}
