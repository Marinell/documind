package com.docanalyzer.ollama;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OllamaEmbeddingRequest {
    private String model;
    private String prompt;
}
