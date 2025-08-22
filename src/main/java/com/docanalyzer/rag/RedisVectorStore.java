package com.docanalyzer.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.json.JsonCommands;
import io.quarkus.redis.datasource.search.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class RedisVectorStore {

    private static final String INDEX_NAME = "idx:document_chunks";
    private static final String PREFIX = "doc:";
    private static final int EMBEDDING_DIMENSION = 768;

    private final JsonCommands<String> jsonCommands;
    private final SearchCommands<String> searchCommands;
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Inject
    public RedisVectorStore(RedisDataSource redisDataSource) {
        this.jsonCommands = redisDataSource.json(String.class);
        this.searchCommands = redisDataSource.search();
        createFtIndex();
    }

    public void createFtIndex() {
        if (searchCommands.ft_list().contains(INDEX_NAME)) {
            return;
        }
        CreateArgs createArgs = new CreateArgs()
                .onJson()
                .prefixes(PREFIX);
        createArgs.indexedField("sessionId", "sessionId", FieldType.TEXT);
        createArgs.indexedField("text", "text", FieldType.TEXT);
        createArgs.indexedField("embedding", "embedding", FieldType.VECTOR);

        searchCommands.ftCreate(INDEX_NAME, createArgs);
    }

    public void addDocumentChunk(String sessionId, String chunkId, String chunkText, double[] embedding) {
        Map<String, Object> doc = Map.of(
                "sessionId", sessionId,
                "text", chunkText,
                "embedding", toByteArray(embedding)
        );
        try {
            jsonCommands.jsonSet(PREFIX + chunkId, "$", objectMapper.writeValueAsString(doc));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<String> findSimilarChunks(String sessionId, double[] queryEmbedding, int k) {
        String query = String.format("@sessionId:%s=>[KNN %d @embedding $query_vector as score]", sessionId, k);
        QueryArgs queryArgs = new QueryArgs()
                .param("query_vector", toByteArray(queryEmbedding))
                .dialect(2);

        List<String> similarChunks = new ArrayList<>();
        searchCommands.ftSearch(INDEX_NAME, query, queryArgs)
                .documents()
                .forEach(doc -> {
                    // Map<String, Object> chunk = objectMapper.readValue(doc.properties()., Map.class);
                    similarChunks.add((String) doc.properties().get("text").asString());
                });
        return similarChunks;
    }

    private byte[] toByteArray(double[] array) {
        ByteBuffer buffer = ByteBuffer.allocate(array.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (double v : array) {
            buffer.putFloat((float) v);
        }
        return buffer.array();
    }
}
