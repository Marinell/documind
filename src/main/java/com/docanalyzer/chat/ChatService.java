package com.docanalyzer.chat;

import com.docanalyzer.anonymization.AnonymizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AllArgsConstructor;
import org.apache.tika.Tika;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
@ApplicationScoped
@AllArgsConstructor
public class ChatService {

    private final AnonymizationService anonymizationService;
    private final DocumentAssistant documentAssistant;

    // In-memory stores for simplicity. For production, consider persistent stores.
    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final Map<String, ChatMemory> chatMemories = new ConcurrentHashMap<>();
    // private final Map<String, DocumentAssistant> assistants = new ConcurrentHashMap<>();


    /*@ConfigProperty(name = "quarkus.langchain4j.openai.api-key")
    String openaiApiKey;*/

    public String createNewChatSession() {
        String sessionId = UUID.randomUUID().toString();
        // Initialize resources for the new session
        chatMemories.put(sessionId, MessageWindowChatMemory.withMaxMessages(10));
        Log.infof("Created new chat session: %s", sessionId);
        return sessionId;
    }

    public void clearChatSession(String sessionId) {
        documents.remove(sessionId);
        chatMemories.remove(sessionId);
        // assistants.remove(sessionId);
        anonymizationService.clearMappingsForSession(sessionId);
        Log.infof("Cleared chat session: %s", sessionId);
    }

    public void ingestDocument(String sessionId, InputStream documentStream, String fileName) throws IOException {
        try {
            StringBuilder documentTexts = new StringBuilder();
            Tika tika = new Tika();
            try {
                    String text = tika.parseToString(documentStream);
                    documentTexts.append(text).append(" --- ");
            } catch (Exception e) {
            }

            Log.infof("document text content: %s", documentTexts.toString());

            String anonymizedContent = anonymizationService.anonymizeDocument(documentTexts.toString(), sessionId);
            documents.put(sessionId, anonymizedContent);
            // createOrUpdateAssistant(sessionId);
        } catch (Exception e) {
            Log.errorf(e, "Error during document ingestion for session %s, file %s", sessionId, fileName);
            throw new ChatServiceException("Failed to ingest document: " + e.getMessage(), e);
        }
    }

    private void createOrUpdateAssistant(String sessionId) {
        ChatMemory chatMemory = chatMemories.get(sessionId);

        if (chatMemory == null) {
            throw new IllegalStateException("Session not properly initialized for assistant creation: " + sessionId);
        }
        /*assistants.put(sessionId, AiServices.builder(DocumentAssistant.class)
                .chatMemory(chatMemory)
                .build());
                */

        Log.infof("Assistant created/updated for session: %s", sessionId);
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CHART_DATA_START_MARKER = "CHART_DATA_START";
    private static final String CHART_DATA_END_MARKER = "CHART_DATA_END";

    public void streamChatResponse(String sessionId, String userMessage,
                                   Consumer<Map<String, Object>> eventConsumer, // Changed to accept a map for different event types
                                   Consumer<String> onComplete, Consumer<Throwable> onError) {
        // DocumentAssistant assistant = assistants.get(sessionId);
        ChatMemory chatMemory = chatMemories.get(sessionId);
        String document = documents.get(sessionId);

        if (documentAssistant == null || chatMemory == null) {
            Log.errorf("Chat session not found or assistant not initialized: %s", sessionId);
            onError.accept(new IllegalStateException("Chat session not initialized or document not processed."));
            return;
        }

        StringBuilder currentTextBuffer = new StringBuilder();
        StringBuilder chartJsonBuffer = new StringBuilder();
        final boolean[] inChartBlock = {false};

        try {



            String response = documentAssistant.chat(userMessage, document);
            String deAnonymizedToken = anonymizationService.deanonymizeResponse(response, sessionId);

            currentTextBuffer.append(deAnonymizedToken);

            sendTextToken(eventConsumer, currentTextBuffer.toString());
            currentTextBuffer.setLength(0);

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
