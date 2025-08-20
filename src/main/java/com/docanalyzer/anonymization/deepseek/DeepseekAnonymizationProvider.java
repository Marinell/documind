package com.docanalyzer.anonymization.deepseek;

import com.docanalyzer.anonymization.AnonymizationProvider;
import com.docanalyzer.huggingface.HuggingFaceClient;
import com.docanalyzer.huggingface.HuggingFaceRequest;
import com.docanalyzer.huggingface.HuggingFaceResponse;
import com.docanalyzer.huggingface.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
@Slf4j
public class DeepseekAnonymizationProvider implements AnonymizationProvider {

    private final DeepseekClient deepseekClient;
    private final HuggingFaceClient huggingFaceClient;
    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "anonymization.api.model")
    String model;

    @ConfigProperty(name = "huggingface.api.token")
    String apiToken;

    public DeepseekAnonymizationProvider(
            @RestClient DeepseekClient deepseekClient,
            ObjectMapper objectMapper,
            @RestClient HuggingFaceClient huggingFaceClient
            ) {
        this.deepseekClient = deepseekClient;
        this.huggingFaceClient = huggingFaceClient;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public AnonymizationResult anonymize(String text, String chatId) throws AnonymizationException {
        String prompt = buildPrompt(text);
        // DeepseekRequest request = new DeepseekRequest(prompt);

        log.info("PROMPT TO ANONYMIZE: \n " + prompt);

        Message message = new Message("user", prompt);
        HuggingFaceRequest request = new HuggingFaceRequest(Collections.singletonList(message), model, false);

        try {
            // DeepseekResponse response = deepseekClient.generate(request);
            HuggingFaceResponse response = huggingFaceClient.createChatCompletion(request, "Bearer " + apiToken);
            return parseResponse(response.getChoices().get(0).getMessage().getContent());
        } catch (Exception e) {
            throw new AnonymizationException("Failed to call Deepseek API", e);
        }
    }

    private String buildPrompt(String text) {
        return "### ROLE ###\n" +
                "Act as a text anonymization specialist tasked with removing all references to people, places, or sensitive information from a given text and replacing them with placeholders in the format [[placeholder_name]]. The goal is to provide the anonymized text along with a JSON mapping of placeholders to their original values.\n" +
                "### CONTEXT ###\n" +
                "- The input text may contain names of people, addresses, or other sensitive information that needs to be anonymized.\n" +
                "- The anonymization process involves replacing sensitive information with placeholders in the format [[placeholder_name]].\n" +
                "- A JSON mapping of placeholders to their original values must be provided.\n" +
                "### TASK ###\n" +
                "Your primary task is to anonymize the given text by replacing all references to people, places, or sensitive information with placeholders in the format [[placeholder_name]]. Then, provide a JSON mapping of these placeholders to their original values.\n" +
                "### EXAMPLE INPUT TEXT ###\n" +
                "For example, the input text is: Marco Marinelli è uno studente che abita in via Ca' Paletta 25/A.\n" +
                "### EXAMPLE ANONYMIZED TEXT ###\n" +
                "[[Person_Name]] è uno studente che abita in [[Address]].\n" +
                "### EXAMPLE PLACEHOLDER MAPPING ###\n" +
                "```json\n" +
                "{\n" +
                "  \"Person_Name\": \"Marco Marinelli\",\n" +
                "  \"Address\": \"via Ca' Paletta 25/A\"\n" +
                "}\n" +
                "```\n" +
                "### OUTPUT FORMAT ###\n" +
                "Provide the final output exclusively in the following format:\n" +
                "Anonymized Text: [[Person_Name]] è uno studente che abita in [[Address]].\n" +
                "Placeholder Mapping: ```json\n" +
                "{\n" +
                "  \"Person_Name\": \"Marco Marinelli\",\n" +
                "  \"Address\": \"via Ca' Paletta 25/A\"\n" +
                "}```\n" +
                "### INPUT TEXT ###\n" +
                "The input text to anonymize is the following: " + text;
    }

    private AnonymizationResult parseResponse(String response) {
        Pattern anonymizedTextPattern = Pattern.compile("Anonymized Text: (.*?)(Placeholder Mapping:|$)");
        Pattern placeholderMappingPattern = Pattern.compile("Placeholder Mapping: ```json(.*?)```", Pattern.DOTALL);

        Matcher anonymizedTextMatcher = anonymizedTextPattern.matcher(response);
        Matcher placeholderMappingMatcher = placeholderMappingPattern.matcher(response);

        if (anonymizedTextMatcher.find() && placeholderMappingMatcher.find()) {
            String anonymizedText = anonymizedTextMatcher.group(1).trim();
            String jsonMapping = placeholderMappingMatcher.group(1).trim();

            try {
                Map<String, String> mappings = objectMapper.readValue(jsonMapping, new TypeReference<Map<String, String>>() {});
                return new AnonymizationResult(anonymizedText, mappings);
            } catch (JsonProcessingException e) {
                throw new AnonymizationException("Failed to parse placeholder mapping JSON", e);
            }
        } else {
            // Fallback or error handling if the response is not in the expected format
            // For now, we'll return the raw response as the anonymized text with no mappings
            return new AnonymizationResult(response, Collections.emptyMap());
        }
    }
}
