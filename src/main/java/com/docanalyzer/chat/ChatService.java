package com.docanalyzer.chat;

import com.docanalyzer.anonymization.AnonymizationService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModelName;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel; // Using OpenAI for now
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.spi.model.embedding.EmbeddingModelFactory;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.ObjectMapper; // For parsing chart JSON
import com.fasterxml.jackson.core.JsonProcessingException;
@ApplicationScoped
public class ChatService {

    private final OpenAiStreamingChatModel chatModel;
    private final EmbeddingModel embeddingModel;
    private final AnonymizationService anonymizationService;

    // In-memory stores for simplicity. For production, consider persistent stores.
    private final Map<String, EmbeddingStore<TextSegment>> embeddingStores = new ConcurrentHashMap<>();
    private final Map<String, ChatMemory> chatMemories = new ConcurrentHashMap<>();
    private final Map<String, DocumentAssistant> assistants = new ConcurrentHashMap<>();

    /*@ConfigProperty(name = "quarkus.langchain4j.openai.api-key")
    String openaiApiKey;*/

    @Inject
    public ChatService(AnonymizationService anonymizationService, @ConfigProperty(name = "quarkus.langchain4j.openai.api-key") String openaiApiKey) {
        // It's generally better to inject models if configured by Quarkus,
        // but streaming model needs specific builder setup for API key if not globally set.
        chatModel = OpenAiStreamingChatModel.builder()
                .apiKey(openaiApiKey)
                .modelName("gpt-3.5-turbo") // Or your preferred model
                .temperature(0.3)
                .build();
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(openaiApiKey)
                .modelName(OpenAiEmbeddingModelName.TEXT_EMBEDDING_ADA_002)
                .build();
        this.anonymizationService = anonymizationService;
    }

    public String createNewChatSession() {
        String sessionId = UUID.randomUUID().toString();
        // Initialize resources for the new session
        embeddingStores.put(sessionId, new InMemoryEmbeddingStore<>());
        chatMemories.put(sessionId, MessageWindowChatMemory.withMaxMessages(10));
        Log.infof("Created new chat session: %s", sessionId);
        return sessionId;
    }

    public void clearChatSession(String sessionId) {
        embeddingStores.remove(sessionId);
        chatMemories.remove(sessionId);
        assistants.remove(sessionId);
        anonymizationService.clearMappingsForSession(sessionId);
        Log.infof("Cleared chat session: %s", sessionId);
    }

    public void ingestDocument(String sessionId, InputStream documentStream, String fileName) throws IOException {
        EmbeddingStore<TextSegment> embeddingStore = embeddingStores.get(sessionId);
        if (embeddingStore == null) {
            Log.errorf("No embedding store found for session: %s. Create a session first.", sessionId);
            throw new IllegalStateException("Session not found or not initialized: " + sessionId);
        }

        // Create a temporary file to use with FileSystemDocumentLoader
        Path tempFile = Files.createTempFile("upload-", "-" + fileName);
        try {
            Files.copy(documentStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            // Read file content as bytes first to avoid encoding issues with Files.readString()
            byte[] fileBytes = Files.readAllBytes(tempFile);
            // Assume UTF-8 for string conversion. If this is still problematic,
            // encoding detection or configuration might be needed.
            String content = new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8);

            // 1. Anonymize the document content
            String anonymizedContent = anonymizationService.anonymizeDocument(content, sessionId);
            Log.debugf("Anonymized content for session %s, file %s: %s", sessionId, fileName, anonymizedContent.substring(0, Math.min(anonymizedContent.length(), 100)) + "...");


            // 2. Create a document from the anonymized content
            // We need a DocumentParser. Let's choose based on file type.
            DocumentParser documentParser;
            if (fileName.toLowerCase().endsWith(".pdf")) {
                documentParser = new ApachePdfBoxDocumentParser();
            } else if (fileName.toLowerCase().endsWith(".txt") || fileName.toLowerCase().endsWith(".md")) {
                documentParser = new TextDocumentParser();
            } else {
                // Fallback or throw error for unsupported types
                Log.warnf("Unsupported file type for parsing: %s. Treating as plain text.", fileName);
                documentParser = new TextDocumentParser();
            }

            Document anonymizedDocument = documentParser.parse(new ByteArrayInputStream(anonymizedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

            // 3. Ingest the anonymized document
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .build();
            ingestor.ingest(anonymizedDocument);
            Log.infof("Document ingested for session: %s, file: %s", sessionId, fileName);

            // Re-create or update the assistant for this session as documents change
            createOrUpdateAssistant(sessionId);

        } finally {
            Files.deleteIfExists(tempFile); // Clean up temporary file
        }
    }

    private void createOrUpdateAssistant(String sessionId) {
        EmbeddingStore<TextSegment> embeddingStore = embeddingStores.get(sessionId);
        ChatMemory chatMemory = chatMemories.get(sessionId);

        if (embeddingStore == null || chatMemory == null) {
            throw new IllegalStateException("Session not properly initialized for assistant creation: " + sessionId);
        }

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3) // How many relevant segments to retrieve
                .minScore(0.6) // Minimum relevance score
                .build();

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(contentRetriever)
                .build();


        assistants.put(sessionId, AiServices.builder(DocumentAssistant.class)
                .streamingChatModel(chatModel)
                .chatMemory(chatMemory)
                .retrievalAugmentor(retrievalAugmentor) // Add this line
                .build());
        Log.infof("Assistant created/updated for session: %s", sessionId);
    }


    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CHART_DATA_START_MARKER = "CHART_DATA_START";
    private static final String CHART_DATA_END_MARKER = "CHART_DATA_END";

    public void streamChatResponse(String sessionId, String userMessage,
                                   Consumer<Map<String, Object>> eventConsumer, // Changed to accept a map for different event types
                                   Consumer<String> onComplete, Consumer<Throwable> onError) {
        DocumentAssistant assistant = assistants.get(sessionId);
        ChatMemory chatMemory = chatMemories.get(sessionId);

        if (assistant == null || chatMemory == null) {
            Log.errorf("Chat session not found or assistant not initialized: %s", sessionId);
            onError.accept(new IllegalStateException("Chat session not initialized or document not processed."));
            return;
        }

        StringBuilder currentTextBuffer = new StringBuilder();
        StringBuilder chartJsonBuffer = new StringBuilder();
        final boolean[] inChartBlock = {false};

        try {
            assistant.chat(userMessage)
                    .onPartialResponse(token -> {
                        String deAnonymizedToken = anonymizationService.deanonymizeResponse(token, sessionId);

                        if (inChartBlock[0]) {
                            chartJsonBuffer.append(deAnonymizedToken);
                            // Check for end marker
                            int endIndex = chartJsonBuffer.indexOf(CHART_DATA_END_MARKER);
                            if (endIndex != -1) {
                                // Extract JSON, remove marker
                                String jsonPayload = chartJsonBuffer.substring(0, endIndex);
                                chartJsonBuffer.setLength(0); // Clear buffer
                                inChartBlock[0] = false;

                                try {
                                    Object chartData = objectMapper.readValue(jsonPayload, Object.class);
                                    Map<String, Object> chartEvent = new HashMap<>();
                                    chartEvent.put("type", "chart");
                                    chartEvent.put("data", chartData);
                                    eventConsumer.accept(chartEvent);
                                    Log.infof("Sent chart data for session %s", sessionId);
                                } catch (JsonProcessingException e) {
                                    Log.errorf(e, "Failed to parse chart JSON for session %s: %s", sessionId, jsonPayload);
                                    // Send the malformed JSON as text, or send an error?
                                    // For now, send the text that couldn't be parsed.
                                    sendTextToken(eventConsumer, CHART_DATA_START_MARKER + jsonPayload + CHART_DATA_END_MARKER);
                                }
                                // Process any text after the end marker in the current token
                                String remainingTokenPart = deAnonymizedToken.substring(deAnonymizedToken.indexOf(CHART_DATA_END_MARKER) + CHART_DATA_END_MARKER.length());
                                if (!remainingTokenPart.isEmpty()) {
                                    sendTextToken(eventConsumer, remainingTokenPart);
                                }
                            }
                        } else {
                            currentTextBuffer.append(deAnonymizedToken);
                            int startIndex = currentTextBuffer.indexOf(CHART_DATA_START_MARKER);
                            if (startIndex != -1) {
                                // Text before marker
                                String textBeforeMarker = currentTextBuffer.substring(0, startIndex);
                                if (!textBeforeMarker.isEmpty()) {
                                    sendTextToken(eventConsumer, textBeforeMarker);
                                }
                                currentTextBuffer.setLength(0); // Clear buffer

                                // Start of chart block found
                                inChartBlock[0] = true;
                                chartJsonBuffer.append(deAnonymizedToken.substring(startIndex + CHART_DATA_START_MARKER.length()));

                                // Check if the end marker is also in this same token
                                int endIndexInSameToken = chartJsonBuffer.indexOf(CHART_DATA_END_MARKER);
                                if (endIndexInSameToken != -1) {
                                    String jsonPayload = chartJsonBuffer.substring(0, endIndexInSameToken);
                                    chartJsonBuffer.setLength(0);
                                    inChartBlock[0] = false;
                                    try {
                                        Object chartData = objectMapper.readValue(jsonPayload, Object.class);
                                        Map<String, Object> chartEvent = new HashMap<>();
                                        chartEvent.put("type", "chart");
                                        chartEvent.put("data", chartData);
                                        eventConsumer.accept(chartEvent);
                                        Log.infof("Sent chart data (within single token block) for session %s", sessionId);
                                    } catch (JsonProcessingException e) {
                                        Log.errorf(e, "Failed to parse chart JSON (within single token block) for session %s: %s", sessionId, jsonPayload);
                                        sendTextToken(eventConsumer, CHART_DATA_START_MARKER + jsonPayload + CHART_DATA_END_MARKER);
                                    }
                                    String remainingTokenPart = deAnonymizedToken.substring(deAnonymizedToken.indexOf(CHART_DATA_END_MARKER) + CHART_DATA_END_MARKER.length());
                                    if (!remainingTokenPart.isEmpty()) {
                                        sendTextToken(eventConsumer, remainingTokenPart);
                                    }
                                }
                            } else {
                                // No marker, just send the text
                                sendTextToken(eventConsumer, deAnonymizedToken);
                                currentTextBuffer.setLength(0); // Assuming tokens are sent whole
                            }
                        }
                    })
                    .onCompleteResponse(response -> {
                        // If there's any remaining text in buffer (e.g. stream ended mid-marker), send it.
                        if (currentTextBuffer.length() > 0) {
                            sendTextToken(eventConsumer, currentTextBuffer.toString());
                            currentTextBuffer.setLength(0);
                        }
                        if (chartJsonBuffer.length() > 0) { // Stream ended while in chart block
                            Log.warnf("Stream ended while in chart block for session %s. Sending buffered content as text.", sessionId);
                            sendTextToken(eventConsumer, CHART_DATA_START_MARKER + chartJsonBuffer.toString());
                            chartJsonBuffer.setLength(0);
                        }
                        Log.infof("Streaming complete for session: %s", sessionId);
                        onComplete.accept("Streaming finished.");
                    })
                    .onError(error -> {
                        // Log.errorf(error, "Error during streaming chat for session: %s", sessionId);
                        // onError.accept(error.);
                    })
                    .start();

        } catch (Exception e) {
            Log.errorf(e, "Failed to process chat message for session %s", sessionId);
            onError.accept(e);
        }
    }

    private void sendTextToken(Consumer<Map<String, Object>> eventConsumer, String text) {
        if (text == null || text.isEmpty()) return;
        Map<String, Object> textEvent = new HashMap<>();
        textEvent.put("type", "token");
        textEvent.put("data", text);
        eventConsumer.accept(textEvent);
    }

}
