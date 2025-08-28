package com.docanalyzer.chat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public record RagConfiguration(String chunkingStrategy,
                               int chunkSize,
                               int chunkOverlap,
                               String llmModel,
                               String embeddingModel,
                               String ingestionStrategy) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @JsonCreator
    public RagConfiguration(@JsonProperty("chunkingStrategy") String chunkingStrategy,
                            @JsonProperty("chunkSize") int chunkSize,
                            @JsonProperty("chunkOverlap") int chunkOverlap,
                            @JsonProperty("llmModel") String llmModel,
                            @JsonProperty("embeddingModel") String embeddingModel,
                            @JsonProperty("ingestionStrategy") String ingestionStrategy) {
        this.chunkingStrategy = chunkingStrategy;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.llmModel = llmModel;
        this.embeddingModel = embeddingModel;
        this.ingestionStrategy = ingestionStrategy != null ? ingestionStrategy : "local";
    }

    public static RagConfiguration fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new RagConfiguration("recursive", 512, 100, "deepseek-r1:1.5b", "nomic-embed-text", "local");
        }
        try {
            // Read the json, and if ingestionStrategy is missing, add it with default value "local"
            ObjectNode node = (ObjectNode) OBJECT_MAPPER.readTree(json);
            if (!node.has("ingestionStrategy")) {
                node.put("ingestionStrategy", "local");
            }
            return OBJECT_MAPPER.treeToValue(node, RagConfiguration.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid RAG configuration format", e);
        }
    }
}
