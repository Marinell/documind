package com.docanalyzer.chat;

import com.docanalyzer.deepseek.DeepseekClient;
import com.docanalyzer.deepseek.DeepseekRequest;
import com.docanalyzer.deepseek.DeepseekResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.tika.Tika;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
@ApplicationScoped
public class ChatService {

    // private final DocumentAssistant documentAssistant;
    private final DeepseekClient deepseekClient;

    // In-memory stores for simplicity. For production, consider persistent stores.
    private final Map<String, String> documents = new ConcurrentHashMap<>();
    private final Map<String, ChatMemory> chatMemories = new ConcurrentHashMap<>();
    // private final Map<String, DocumentAssistant> assistants = new ConcurrentHashMap<>();

    @Inject
    public ChatService(@RestClient DeepseekClient deepseekClient) {
        this.deepseekClient = deepseekClient;
    }

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

            // Log.infof("document text content: %s", documentTexts.toString());
            documents.put(sessionId, documentTexts.toString());
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

        if ( chatMemory == null) {
            Log.errorf("Chat session not found or assistant not initialized: %s", sessionId);
            onError.accept(new IllegalStateException("Chat session not initialized or document not processed."));
            return;
        }

        StringBuilder currentTextBuffer = new StringBuilder();
        StringBuilder chartJsonBuffer = new StringBuilder();
        final boolean[] inChartBlock = {false};

        try {

            DeepseekRequest request = new DeepseekRequest(buildPrompt(userMessage, document));

            DeepseekResponse response = deepseekClient.generate(request);

            currentTextBuffer.append(response.getResponse());

            sendTextToken(eventConsumer, currentTextBuffer.toString());
            currentTextBuffer.setLength(0);

        } catch (Exception e) {
            Log.errorf(e, "Failed to process chat message for session %s", sessionId);
            onError.accept(e);
        }
    }

    private String buildPrompt(String userMessage, String document) {
        return "You are an expert document assistant, specialized in the business, financial, tax and legal sector.\n" +
                "        Your task is to analyze the provided document text and answer the user query about the document.\n" +
                "        Focus on identifying key information.\n" +
                "        Do not mention that you are an AI. Response in markdown format. If you don't know the answer, say so.\n" +
                "        The document to analyze is the following: " + document +
                " \n The user query on the document is the following: " + userMessage;
    }

    private void sendTextToken(Consumer<Map<String, Object>> eventConsumer, String text) {
        if (text == null || text.isEmpty()) return;
        Map<String, Object> textEvent = new HashMap<>();
        textEvent.put("type", "token");
        textEvent.put("data", text);
        eventConsumer.accept(textEvent);
    }

}
