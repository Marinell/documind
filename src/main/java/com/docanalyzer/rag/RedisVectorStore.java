package com.docanalyzer.rag;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.search.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class RedisVectorStore {

    private static final String INDEX_NAME = "idx:document_chunks";
    private static final String PREFIX = "doc:";
    private static final int EMBEDDING_DIMENSION = 768; // 1024: bge

    private final HashCommands<String, String, Object> hashCommands;
    private final SearchCommands<String> searchCommands;


    @Inject
    public RedisVectorStore(RedisDataSource redisDataSource) {
        this.hashCommands = redisDataSource.hash(String.class, String.class, Object.class);
        this.searchCommands = redisDataSource.search();
        createFtIndex();
    }

    public void createFtIndex() {
        if (searchCommands.ft_list().contains(INDEX_NAME)) {
            return;
        }
        CreateArgs createArgs = new CreateArgs()
                .onHash()
                .prefixes(PREFIX);

        createArgs.indexedField("sessionId", "sessionId", FieldType.TEXT);
        createArgs.indexedField("text", "text", FieldType.TEXT);
        createArgs.indexedField("id", "id", FieldType.NUMERIC);

        FieldOptions options = new FieldOptions();
        options.vectorAlgorithm(VectorAlgorithm.HNSW);
        options.distanceMetric(DistanceMetric.COSINE);
        options.dimension(EMBEDDING_DIMENSION);
        options.vectorType(VectorType.FLOAT64);
        createArgs.indexedField("embedding", "embedding", FieldType.VECTOR, options);

        searchCommands.ftCreate(INDEX_NAME, createArgs);
    }

    public void addDocumentChunk(String sessionId, int chunkId, String chunkText, double[] embedding) {
        String sanitizedSessionId = sanitizeSessionId(sessionId);
        String key = PREFIX + sanitizedSessionId + "_" + chunkId;

        Map<String, Object> doc = new HashMap<>();
        doc.put("id", String.valueOf(chunkId));
        doc.put("sessionId", sanitizedSessionId);
        doc.put("text", chunkText);
        doc.put("embedding", toByteArray(embedding));

        hashCommands.hset(key, doc);
    }

    public List<String> findSimilarChunks(String sessionId, double[] queryEmbedding, int k) {
        String query = String.format("(@sessionId:$sessionId)=>[KNN %d @embedding $query_vector as score]", k);
        QueryArgs queryArgs = new QueryArgs()
                .param("query_vector", toByteArray(queryEmbedding))
                .param("sessionId", sanitizeSessionId(sessionId))
                .sortByAscending("id")
                .dialect(2);

        List<String> similarChunks = new ArrayList<>();

        List<Document> documents = searchCommands.ftSearch(INDEX_NAME, query, queryArgs).documents();
        if (documents.isEmpty()) {
            log.warn("no documents found for sessionId {}", sessionId);
            query = "*";
            queryArgs = new QueryArgs()
                    .sortByAscending("id")
                    .dialect(2)
                    .limit(0, 1000);
            documents = searchCommands.ftSearch(INDEX_NAME, query, queryArgs).documents();
        }
        documents.forEach(doc -> {
            List<String> res = doc.properties().values().stream()
                    .filter(d -> d.name().equals("text"))
                    .map(Document.Property::asString)
                    .toList();
            similarChunks.addAll(res);
        });
        return similarChunks;
    }

    private byte[] toByteArray(double[] array) {
        ByteBuffer buffer = ByteBuffer.allocate(array.length * Double.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (double v : array) {
            buffer.putDouble(v);
        }
        return buffer.array();
    }

    private String sanitizeSessionId(String input) {
        return input.replace("-", "");
    }
}
