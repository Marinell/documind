package com.docanalyzer.groundx;

import com.docanalyzer.groundx.model.CreateBucketRequest;
import com.docanalyzer.groundx.model.SearchRequest;
import com.docanalyzer.groundx.model.SearchResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class GroundxService {

    @Inject
    @RestClient
    GroundxClient groundxClient;

    @ConfigProperty(name = "groundx.api.key")
    String apiKey;

    public int ingestDocument(File file, String fileName, String fileType) throws InterruptedException {
        // 1. Create a bucket
        String bucketName = "docanalyzer-" + UUID.randomUUID().toString();
        CreateBucketRequest createBucketRequest = new CreateBucketRequest(bucketName);
        int bucketId = groundxClient.createBucket(createBucketRequest, apiKey).bucket.bucketId;

        // 2. Ingest the document
        GroundxClient.IngestForm ingestForm = new GroundxClient.IngestForm();
        ingestForm.bucketId = bucketId;
        ingestForm.file = file;
        ingestForm.fileName = fileName;
        ingestForm.fileType = fileType;

        String processId = groundxClient.ingest(ingestForm, apiKey).ingest.processId;

        // 3. Poll for completion
        long startTime = System.currentTimeMillis();
        long timeout = 300000; // 5 minutes

        while (System.currentTimeMillis() - startTime < timeout) {
            String status = groundxClient.getProcessingStatus(processId, apiKey).ingest.status;
            if ("complete".equalsIgnoreCase(status)) {
                return bucketId; // Success
            }
            if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
                throw new RuntimeException("GroundX document ingestion failed with status: " + status);
            }
            Thread.sleep(2000); // Poll every 2 seconds
        }

        throw new RuntimeException("GroundX document ingestion timed out after " + (timeout / 1000) + " seconds.");
    }

    public List<String> search(int bucketId, String query) {
        SearchRequest searchRequest = new SearchRequest(query);
        SearchResponse searchResponse = groundxClient.searchContent(bucketId, searchRequest, apiKey);

        return searchResponse.search.results.stream()
                .map(result -> result.text)
                .collect(Collectors.toList());
    }
}
