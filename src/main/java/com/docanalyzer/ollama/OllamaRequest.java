package com.docanalyzer.ollama;

import lombok.Data;

@Data
public class OllamaRequest {

    private String model;
    private String prompt;
    private boolean stream = false;

    public OllamaRequest(String model, String prompt) {
        this.model = model;
        this.prompt = prompt;
    }
}
