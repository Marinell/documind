package com.docanalyzer.chat;

import com.docanalyzer.ollama.OllamaClient;
import com.docanalyzer.ollama.OllamaEmbeddingResponse;
import com.docanalyzer.ollama.OllamaResponse;
import com.docanalyzer.rag.RedisVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChatServiceTest {

    @InjectMocks
    ChatService chatService;

    @Mock
    OllamaClient ollamaClient;

    @Mock
    RedisVectorStore vectorStore;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mocking the embedding response
        OllamaEmbeddingResponse embeddingResponse = new OllamaEmbeddingResponse();
        embeddingResponse.setEmbedding(new double[]{0.1, 0.2, 0.3});
        when(ollamaClient.embed(any())).thenReturn(embeddingResponse);

        // Mocking the chat response
        OllamaResponse ollamaResponse = new OllamaResponse();
        ollamaResponse.setResponse("This is a test response.");
        when(ollamaClient.generate(any())).thenReturn(ollamaResponse);
    }

    @Test
    void testIngestDocument() throws IOException {
        String sessionId = "test-session";
        String fileName = "test.txt";
        String documentText = "This is a test document.";
        InputStream documentStream = new ByteArrayInputStream(documentText.getBytes());

        doNothing().when(vectorStore).addDocumentChunk(anyString(), any(float[].class));

        chatService.ingestDocument(sessionId, documentStream, fileName);

        verify(vectorStore).addDocumentChunk(anyString(), any(float[].class));
    }

    @Test
    void testStreamChatResponse() {
        String sessionId = "test-session";
        String userMessage = "What is this document about?";

        when(vectorStore.findSimilarChunks(any(float[].class), anyInt()))
                .thenReturn(Collections.singletonList("This is a test document."));

        AtomicReference<String> response = new AtomicReference<>();
        Consumer<Map<String, Object>> eventConsumer = event -> {
            if ("token".equals(event.get("type"))) {
                response.set((String) event.get("data"));
            }
        };
        Consumer<String> onComplete = s -> {};
        Consumer<Throwable> onError = t -> fail(t);

        chatService.streamChatResponse(sessionId, userMessage, eventConsumer, onComplete, onError);

        assertEquals("This is a test response.", response.get());
    }
}
