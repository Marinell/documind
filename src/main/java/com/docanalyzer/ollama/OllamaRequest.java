package com.docanalyzer.ollama;

import lombok.Data;

@Data
public class OllamaRequest {

    private String model = "deepseek-r1:1.5b";//"smollm2:1.7b"; // or whatever model is appropriate
    private String prompt;
    private boolean stream = false;
}
