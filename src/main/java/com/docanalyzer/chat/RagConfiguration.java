package com.docanalyzer.chat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public record RagConfiguration(String chunkingStrategy,
                               int chunkSize,
                               int chunkOverlap,
                               String llmModel,
                               String embeddingModel) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @JsonCreator
    public RagConfiguration(@JsonProperty("chunkingStrategy") String chunkingStrategy,
                            @JsonProperty("chunkSize") int chunkSize,
                            @JsonProperty("chunkOverlap") int chunkOverlap,
                            @JsonProperty("llmModel") String llmModel,
                            @JsonProperty("embeddingModel") String embeddingModel) {
        this.chunkingStrategy = chunkingStrategy;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.llmModel = llmModel;
        this.embeddingModel = embeddingModel;
    }

    public static RagConfiguration fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new RagConfiguration("recursive", 512, 100, "deepseek-r1:1.5b", "nomic-embed-text");
        }
        try {
            return OBJECT_MAPPER.readValue(json, RagConfiguration.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid RAG configuration format", e);
        }
    }
}
