package com.docanalyzer.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.json.JsonCommands;
import io.quarkus.redis.datasource.search.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Slf4j
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

        createArgs.indexedField("$.sessionId", "sessionId", FieldType.TEXT);
        createArgs.indexedField("$.text", "text", FieldType.TEXT);
        createArgs.indexedField("$.id", "id", FieldType.NUMERIC);
        FieldOptions options = new FieldOptions();
        options.vectorAlgorithm(VectorAlgorithm.HNSW);
        options.distanceMetric(DistanceMetric.COSINE);
        options.dimension(EMBEDDING_DIMENSION);
        options.vectorType(VectorType.FLOAT32);
        createArgs.indexedField("$.embedding", "embedding", FieldType.VECTOR, options);

        searchCommands.ftCreate(INDEX_NAME, createArgs);
    }

    public void addDocumentChunk(String sessionId, int chunkId, String chunkText, double[] embedding) {
        String sanitizedSessionId = sanitizeSessionId(sessionId);
        Map<String, Object> doc = Map.of(
                "id", chunkId,
                "sessionId", sanitizedSessionId,
                "text", chunkText,
                "embedding", toByteArray(embedding)
        );
        StringBuilder sb = new StringBuilder();
        try {
            jsonCommands.jsonSet(sb.append(PREFIX).append(sanitizedSessionId).append("_").append(chunkId).toString(),
                    "$",
                    objectMapper.writeValueAsString(doc));
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

    public List<String> findSimilarChunks(String sessionId, double[] queryEmbedding, int k) {
        String query = String.format("*=>[KNN %d @embedding $query_vector as score]", k);
        QueryArgs queryArgs = new QueryArgs()
                .param("query_vector", toByteArray(queryEmbedding))
                .dialect(2);

        List<String> similarChunks = new ArrayList<>();

        List<Document> documents = searchCommands.ftSearch(INDEX_NAME, query, queryArgs).documents();
        if (documents.isEmpty()) {
            log.warn("no documents found");
            query = "*";
            queryArgs = new QueryArgs()
                    .sortByAscending("id")
                    .dialect(2)
                    .limit(0, 1000);
            documents = searchCommands.ftSearch(INDEX_NAME, query, queryArgs).documents();
        }
        documents.forEach(doc -> {
            doc.properties().values().forEach(d ->
                    similarChunks.add(d.asJsonObject().getString("text"))
            );
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

    private String sanitizeSessionId(String input) {
        return input.replace("-", "");
    }
}
