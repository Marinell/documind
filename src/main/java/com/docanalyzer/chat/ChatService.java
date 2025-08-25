package com.docanalyzer.chat;

import com.docanalyzer.ollama.OllamaClient;
import com.docanalyzer.ollama.OllamaEmbeddingRequest;
import com.docanalyzer.ollama.OllamaRequest;
import com.docanalyzer.ollama.OllamaResponse;
import com.docanalyzer.rag.DocumentSplitter;
import com.docanalyzer.rag.RedisVectorStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jfree.chart.JFreeChart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class ChatService {

    private final OllamaClient ollamaClient;
    private final ChartService chartService;
    private final RedisVectorStore vectorStore;
    private final DocumentSplitter documentSplitter;

    @Inject
    public ChatService(@RestClient OllamaClient ollamaClient, ChartService chartService, RedisVectorStore vectorStore) {
        this.ollamaClient = ollamaClient;
        this.chartService = chartService;
        this.vectorStore = vectorStore;
        this.documentSplitter = new DocumentSplitter(512, 100);
    }

    public String createNewChatSession() {
        String sessionId = UUID.randomUUID().toString();
        Log.infof("Created new chat session: %s", sessionId);
        return sessionId;
    }

    public void ingestDocument(String sessionId, InputStream documentStream, String fileName) throws IOException {
        try {
            Tika tika = new Tika();
            String text = tika.parseToString(documentStream);
            List<String> chunks = documentSplitter.split(text);
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                OllamaEmbeddingRequest request = new OllamaEmbeddingRequest("nomic-embed-text", chunk);
                double[] doubleEmbedding = ollamaClient.embed(request).getEmbedding();
                float[] floatEmbedding = new float[doubleEmbedding.length];
                for (int j = 0; j < doubleEmbedding.length; j++) {
                    floatEmbedding[j] = (float) doubleEmbedding[j];
                }
                vectorStore.addDocumentChunk(chunk, floatEmbedding);
            }
        } catch (Exception e) {
            Log.errorf(e, "Error during document ingestion for session %s, file %s", sessionId, fileName);
            throw new ChatServiceException("Failed to ingest document: " + e.getMessage(), e);
        }
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CHART_DATA_START_MARKER = "CHART_DATA_START";
    private static final String CHART_DATA_END_MARKER = "CHART_DATA_END";

    public void streamChatResponse(String sessionId, String userMessage,
                                   Consumer<Map<String, Object>> eventConsumer,
                                   Consumer<String> onComplete, Consumer<Throwable> onError) {
        OllamaEmbeddingRequest embeddingRequest = new OllamaEmbeddingRequest("nomic-embed-text", userMessage);
        double[] doubleUserQueryEmbedding = ollamaClient.embed(embeddingRequest).getEmbedding();
        float[] floatUserQueryEmbedding = new float[doubleUserQueryEmbedding.length];
        for (int i = 0; i < doubleUserQueryEmbedding.length; i++) {
            floatUserQueryEmbedding[i] = (float) doubleUserQueryEmbedding[i];
        }

        List<String> similarChunks = vectorStore.findSimilarChunks(floatUserQueryEmbedding, 5);
        if (similarChunks.isEmpty()) {
            sendTextToken(eventConsumer, "no matches found in document");
            return;
        }
        String context = String.join(" ", similarChunks);

        log.info("CONTEXT: " + context);

        try {
            OllamaRequest request = new OllamaRequest(buildPrompt(userMessage, context));
            log.info("deepseek request:\n" + request.getPrompt());

            OllamaResponse response = ollamaClient.generate(request);
            log.info("deepseek response:\n" + response.getResponse());

            String fullResponse = parseResponse(response.getResponse());
            int chartStart = fullResponse.indexOf(CHART_DATA_START_MARKER);

            if (chartStart != -1) {
                String textPart = fullResponse.substring(0, chartStart).trim();
                if (!textPart.isEmpty()) {
                    sendTextToken(eventConsumer, textPart);
                }

                int chartEnd = fullResponse.indexOf(CHART_DATA_END_MARKER, chartStart);
                if (chartEnd != -1) {
                    String chartJson = fullResponse.substring(chartStart + CHART_DATA_START_MARKER.length(), chartEnd).trim();
                    handleChartData(sessionId, chartJson, eventConsumer, onError);

                    String remainingText = fullResponse.substring(chartEnd + CHART_DATA_END_MARKER.length()).trim();
                    if (!remainingText.isEmpty()) {
                        sendTextToken(eventConsumer, remainingText);
                    }
                } else {
                    sendTextToken(eventConsumer, fullResponse.substring(chartStart));
                }
            } else {
                sendTextToken(eventConsumer, fullResponse);
            }

        } catch (Exception e) {
            Log.errorf(e, "Failed to process chat message for session %s", sessionId);
            onError.accept(e);
        } finally {
            onComplete.accept("Stream finished");
        }
    }

    private String parseResponse(String response) {
        if (!response.contains("<think>")) {
            return response;
        }
        String parsedResponse = response.replace(response.substring(response.indexOf("<think>"), response.indexOf("</think>")+8), "");
        log.info("\nparsed response: \n" + parsedResponse);
        return parsedResponse;
    }

    private void handleChartData(String sessionId, String chartJson, Consumer<Map<String, Object>> eventConsumer, Consumer<Throwable> onError) {
        try {
            Chart chart = objectMapper.readValue(chartJson, Chart.class);

            List<String> labels = new ArrayList<>();
            for (Chart.Dataset data: chart.getDatasets()) {
                labels.add(data.getLabel());
                labels.add(data.getLabel());
            }

            chart.setLabels(labels);

            JFreeChart jfreechart = chartService.createChart(chart);
            String chartFileName = "chart-" + sessionId + "-" + System.currentTimeMillis() + ".png";
            String chartDir = "charts";
            new File(chartDir).mkdirs();
            String filePath = chartDir + File.separator + chartFileName;
            chartService.saveChartAsPng(jfreechart, filePath);

            Map<String, Object> chartEvent = new HashMap<>();
            chartEvent.put("type", "chart");
            chartEvent.put("data", chart);
            eventConsumer.accept(chartEvent);
        } catch (JsonProcessingException e) {
            Log.errorf(e, "Failed to parse chart JSON for session %s: %s", sessionId, chartJson);
            sendTextToken(eventConsumer, "I received chart data in a format I couldn't understand. Please try again.");
        } catch (IOException e) {
            Log.errorf(e, "Error processing or saving chart for session %s", sessionId);
            onError.accept(new RuntimeException("Failed to generate or save chart.", e));
        } catch (Exception e) {
            Log.errorf(e, "An unexpected error occurred while handling chart data for session %s", sessionId);
            onError.accept(new RuntimeException("An unexpected error occurred with the chart data.", e));
        }
    }

    private String buildPrompt(String userMessage, String document) {
         return """
                 ### ROLE ###
                 
                 Act as an expert document assistant, specialized in the business, financial, tax, and legal sector. Your role is to analyze the provided document text and answer the user's query about the document.
                 
                 ### CONTEXT ###
                 
                 - **Document Text Content**: The specific content of the document to be analyzed. (This will be provided separately.)
                 - **User Query**: The user's question or request regarding the document. (This will be provided separately.)
                 
                 ### TASK ###
                 
                 Your primary task is to analyze the provided document text and respond to the user's query about the document.\s
                 
                 Follow these steps precisely:
                 
                 1. Carefully read and understand the document text content.
                 2. Analyze the user's query to identify what information is being requested.
                 3. Extract or derive the required information from the document text.
                 4. If the user requests a chart, generate the data for the chart in JSON format.
                 5. Format the response in markdown.
                 
                 ### EXAMPLES ###
                 
                 Example 1:
                 - Input: User asks for a summary of a specific section in the document.
                 - Rationale: Identify key points in the specified section.
                 - Output:\s
                   ```markdown
                   ## Summary of Section X
                   The section discusses [key points].
                   ```
                 
                 Example 2:
                 - Input: User requests a chart of financial data mentioned in the document.
                 - Rationale: Identify and extract relevant financial data.
                 - Output:\s
                   ```
                   CHART_DATA_START
                   {
                     "chartType": "bar",
                     "title": "Financial Data Overview",
                     "datasets": [
                       {
                         "label": "Revenue",
                         "data": [100000, 50000, 50000]
                       },
                       {
                         "label": "Expenses",
                         "data": [3456, 1111, 2500]
                       },
                        {
                         "label": "Profit",
                         "data": [150000, 40000, 60000]
                       }
                     ]
                   }
                   CHART_DATA_END
                   ```
                 
                 ### CONSTRAINTS ###
                 
                 - **Tone**: Professional and objective.
                 - **Style**: Clear and concise.
                 - **Length**: As necessary to fully address the user's query.
                 - **Do Not**: Mention that you are an AI.
                 - **Format**: Responses must be in markdown format.
                 - **Chart Data**: JSON formatted data enclosed in CHART_DATA_START and CHART_DATA_END.
                 
                 ### OUTPUT FORMAT ###
                 
                 Provide the final output exclusively in the following format:
                 
                 - For text responses:
                   ```markdown
                   ## Response
                   [Your response in markdown format.]
                   ```
                 - For chart requests:
                   ```
                   CHART_DATA_START
                   {
                     "chartType": "bar|pie",
                     "title": "...",
                     "datasets": [
                       {
                         "label": "label_1",
                         "data": [data_1, data_2, data_3, ... , data_n]
                       },
                       {
                         "label": "label_2",
                         "data": [data_1, data_2, data_3, ... , data_n]
                       },
                       {
                         "label": "label_3",
                         "data": [data_1, data_2, data_3, ... , data_n]
                       },
                       {
                         "label": "label_n",
                         "data": [data_1, data_2, data_3, ... , data_n]
                       }
                     ]
                   }
                   CHART_DATA_END
                   ```"""
                 + "\nThe user query is: " + userMessage
                 + "\nThe document text is: " + document;
    }

    private void sendTextToken(Consumer<Map<String, Object>> eventConsumer, String text) {
        if (text == null || text.isEmpty()) return;
        Map<String, Object> textEvent = new HashMap<>();
        textEvent.put("type", "token");
        textEvent.put("data", text);
        eventConsumer.accept(textEvent);
    }
}
