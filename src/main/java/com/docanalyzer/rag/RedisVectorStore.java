package com.docanalyzer.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import io.quarkiverse.langchain4j.redis.RedisEmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class RedisVectorStore {

    @Inject
    RedisEmbeddingStore embeddingStore;

    public void addDocumentChunk(String chunkText, float[] embedding) {
        TextSegment segment = TextSegment.from(chunkText);
        Embedding emb = Embedding.from(embedding);
        embeddingStore.add(emb, segment);
    }

    public List<String> findSimilarChunks(float[] queryEmbedding, int k) {
        Embedding queryEmbedding_ = Embedding.from(queryEmbedding);
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(queryEmbedding_, k);
        return relevant.stream()
                .map(embeddingMatch -> embeddingMatch.embedded().text())
                .collect(Collectors.toList());
    }
}
