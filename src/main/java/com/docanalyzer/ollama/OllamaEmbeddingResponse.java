package com.docanalyzer.ollama;

import lombok.Data;

@Data
public class OllamaEmbeddingResponse {
    private double[] embedding;
}
