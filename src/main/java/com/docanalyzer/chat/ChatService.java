package com.docanalyzer.chat;

import com.docanalyzer.anonymization.AnonymizationService;
import com.docanalyzer.huggingface.HuggingFaceClient;
import com.docanalyzer.huggingface.HuggingFaceRequest;
import com.docanalyzer.huggingface.HuggingFaceResponse;
import com.docanalyzer.huggingface.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.apache.tika.Tika;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@ApplicationScoped
public class ChatService {

    @Inject
    AnonymizationService anonymizationService;

    @Inject
    @RestClient
    HuggingFaceClient huggingFaceClient;

    @ConfigProperty(name = "huggingface.api.token")
    String apiToken;

    @ConfigProperty(name = "huggingface.api.model")
    String model;

    private final Map<String, String> documents = new ConcurrentHashMap<>();

    public String createNewChatSession() {
        return UUID.randomUUID().toString();
    }

    public void clearChatSession(String sessionId) {
        documents.remove(sessionId);
        anonymizationService.clearMappingsForSession(sessionId);
        Log.infof("Cleared chat session: %s", sessionId);
    }

    public void ingestDocument(String sessionId, InputStream documentStream, String fileName) throws IOException {
        try {
            Tika tika = new Tika();
            String text = tika.parseToString(documentStream);
            String anonymizedContent = anonymizationService.anonymizeDocument(text, sessionId);
            documents.put(sessionId, anonymizedContent);
        } catch (Exception e) {
            Log.errorf(e, "Error during document ingestion for session %s, file %s", sessionId, fileName);
            throw new ChatServiceException("Failed to ingest document: " + e.getMessage(), e);
        }
    }

    public void streamChatResponse(String sessionId, String userMessage,
                                   Consumer<Map<String, Object>> eventConsumer,
                                   Consumer<String> onComplete, Consumer<Throwable> onError) {
        String document = documents.get(sessionId);

        if (document == null) {
            Log.errorf("Chat session not found or document not processed: %s", sessionId);
            onError.accept(new IllegalStateException("Chat session not initialized or document not processed."));
            return;
        }

        try {
            String prompt = "Document: " + document + "\n\n" + "User query: " + userMessage;
            Message message = new Message("user", prompt);
            HuggingFaceRequest request = new HuggingFaceRequest(Collections.singletonList(message), model, false);

            HuggingFaceResponse response = huggingFaceClient.createChatCompletion(request, "Bearer " + apiToken);

            String responseContent = response.getChoices().get(0).getMessage().getContent();
            String deAnonymizedToken = anonymizationService.deanonymizeResponse(responseContent, sessionId);

            sendTextToken(eventConsumer, deAnonymizedToken);
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
