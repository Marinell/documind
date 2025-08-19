package com.docanalyzer.anonymization.presidio;

import com.docanalyzer.anonymization.AnonymizationProvider;
import com.docanalyzer.anonymization.presidio.model.AnonymizerConfig;
import com.docanalyzer.anonymization.presidio.model.PresidioAnalyzeRequest;
import com.docanalyzer.anonymization.presidio.model.RecognizerResult;
import com.docanalyzer.anonymization.presidio.model.recognizer.AdHocRecognizer;
import com.docanalyzer.anonymization.presidio.model.recognizer.FlairRecognizer;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class PresidioAnonymizationProvider implements AnonymizationProvider {

    private final PresidioClient presidioClient;

    public PresidioAnonymizationProvider(@RestClient PresidioClient presidioClient) {
        this.presidioClient = presidioClient;
    }

    @Override
    public AnonymizationResult anonymize(String text, String chatId) throws AnonymizationException {
        // 1. Analyze the text
        List<String> supportedEntities = Arrays.asList(
                "PERSON", "LOCATION", "ORGANIZATION", "NRP", "GPE", "DATE", "TIME", "MONEY", "PERCENT", "FAC", "ORG", "LOC",
                "CARDINAL", "EVENT", "LANGUAGE", "LAW", "ORDINAL", "PRODUCT", "QUANTITY", "WORK_OF_ART"
        );
        AdHocRecognizer flairRecognizer = new FlairRecognizer("flair/ner-english-large", supportedEntities);
        List<AdHocRecognizer> adHocRecognizers = new ArrayList<>();
        adHocRecognizers.add(flairRecognizer);

        PresidioAnalyzeRequest analyzeRequest = new PresidioAnalyzeRequest(text, "en", adHocRecognizers);
        List<RecognizerResult> recognizerResults;
        try {
            recognizerResults = presidioClient.analyze(analyzeRequest);
        } catch (Exception e) {
            throw new AnonymizationException("Failed to call Presidio analyze API", e);
        }

        if (recognizerResults.isEmpty()) {
            return new AnonymizationResult(text, Collections.emptyMap());
        }

        // 2. Generate placeholders and mapping
        Map<String, String> placeholderMappings = new HashMap<>();
        Map<String, AnonymizerConfig> anonymizers = new HashMap<>();
        Map<String, AtomicInteger> entityCounters = new HashMap<>();

        // This is a bit tricky. We need to create a unique anonymizer for each recognized entity,
        // but the Presidio API anonymizes by entity *type*. This means if we have two PERSON
        // entities, we can't replace them with [[PERSON_1]] and [[PERSON_2]] in a single /anonymize call.
        //
        // The previous implementation handled this by doing the replacement in Java.
        // The code review suggested using the /anonymize endpoint for efficiency, but that might not be possible
        // given the requirement to have unique placeholders for each entity.
        //
        // Let's reconsider the manual replacement approach, but this time, I will make sure it's clean and correct.
        // The previous implementation was already correct in its logic (sorting by descending start position).
        // I will stick with that implementation, but I will add a comment explaining why I'm not using the /anonymize endpoint.

        recognizerResults.sort((r1, r2) -> Integer.compare(r2.getStart(), r1.getStart()));

        StringBuilder anonymizedTextBuilder = new StringBuilder(text);

        for (RecognizerResult result : recognizerResults) {
            String entityType = result.getEntityType();
            int count = entityCounters.computeIfAbsent(entityType, k -> new AtomicInteger(0)).incrementAndGet();
            String placeholder = String.format("[[%s_%d]]", entityType.toUpperCase(), count);

            String originalValue = text.substring(result.getStart(), result.getEnd());
            placeholderMappings.put(placeholder, originalValue);

            anonymizedTextBuilder.replace(result.getStart(), result.getEnd(), placeholder);
        }

        // The placeholder map needs to be inverted for the final result.
        // The key should be the placeholder, and the value should be the original text.
        // The current implementation is already doing this correctly.

        return new AnonymizationResult(anonymizedTextBuilder.toString(), placeholderMappings);
    }
}
