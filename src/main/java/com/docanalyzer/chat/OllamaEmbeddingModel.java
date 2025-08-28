package com.docanalyzer.chat;

import com.docanalyzer.ollama.OllamaClient;
import com.docanalyzer.ollama.OllamaEmbeddingRequest;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.List;
import java.util.stream.Collectors;

public class OllamaEmbeddingModel implements EmbeddingModel {

    private final OllamaClient ollamaClient;
    private final String modelName;

    public OllamaEmbeddingModel(OllamaClient ollamaClient, String modelName) {
        this.ollamaClient = ollamaClient;
        this.modelName = modelName;
    }

    @Override
    public Response<Embedding> embed(String text) {
        OllamaEmbeddingRequest request = new OllamaEmbeddingRequest(modelName, text);
        double[] doubleEmbedding = ollamaClient.embed(request).getEmbedding();
        float[] floatEmbedding = new float[doubleEmbedding.length];
        for (int i = 0; i < doubleEmbedding.length; i++) {
            floatEmbedding[i] = (float) doubleEmbedding[i];
        }
        return Response.from(Embedding.from(floatEmbedding));
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = textSegments.stream()
                .map(segment -> embed(segment.text()).content())
                .collect(Collectors.toList());
        return Response.from(embeddings);
    }
}
