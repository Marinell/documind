package com.docanalyzer.anonymization.opennlp;

import com.docanalyzer.anonymization.AnonymizationProvider;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@ApplicationScoped
public class OpenNlpAnonymizationProvider implements AnonymizationProvider {

    private TokenizerME tokenizer;
    private NameFinderME[] nameFinders;
    private static final String[] modelPaths = {
            "models/en-ner-person.bin",
            "models/en-ner-location.bin",
            "models/en-ner-organization.bin"
    };
    private static final String TOKENIZER_MODEL_PATH = "models/en-token.bin";

    @PostConstruct
    void init() {
        try {
            // Load tokenizer model
            try (InputStream tokenizerModelIn = new FileInputStream(TOKENIZER_MODEL_PATH)) {
                TokenizerModel tokenizerModel = new TokenizerModel(tokenizerModelIn);
                tokenizer = new TokenizerME(tokenizerModel);
            }

            // Load NER models
            nameFinders = new NameFinderME[modelPaths.length];
            for (int i = 0; i < modelPaths.length; i++) {
                try (InputStream modelIn = new FileInputStream(modelPaths[i])) {
                    TokenNameFinderModel model = new TokenNameFinderModel(modelIn);
                    nameFinders[i] = new NameFinderME(model);
                }
            }
        } catch (IOException e) {
            log.error("Failed to load OpenNLP models. Anonymization will not work. Please ensure models are available at the specified paths.", e);
            throw new AnonymizationException("Failed to initialize OpenNLP models", e);
        }
    }

    @Override
    public AnonymizationResult anonymize(String text, String chatId) throws AnonymizationException {
        if (tokenizer == null || nameFinders == null) {
            log.warn("OpenNLP models not loaded. Returning original text.");
            return new AnonymizationResult(text, Collections.emptyMap());
        }

        String[] tokens = tokenizer.tokenize(text);
        if (tokens.length == 0) {
            return new AnonymizationResult(text, Collections.emptyMap());
        }
        Span[] tokenSpans = tokenizer.tokenizePos(text);

        List<Span> allNameSpans = new ArrayList<>();
        for (NameFinderME nameFinder : nameFinders) {
            Span[] nameSpans = nameFinder.find(tokens);
            allNameSpans.addAll(Arrays.asList(nameSpans));
            nameFinder.clearAdaptiveData(); // Clear adaptive data between documents
        }

        List<Span> characterSpans = new ArrayList<>();
        for (Span tokenSpan : allNameSpans) {
            int start = tokenSpans[tokenSpan.getStart()].getStart();
            int end = tokenSpans[tokenSpan.getEnd() - 1].getEnd();
            String type = tokenSpan.getType();
            characterSpans.add(new Span(start, end, type));
        }

        List<Span> filteredSpans = filterOverlappingSpans(characterSpans);
        filteredSpans.sort(Comparator.comparingInt(Span::getStart).reversed());

        StringBuilder anonymizedTextBuilder = new StringBuilder(text);
        Map<String, String> placeholderMappings = new HashMap<>();
        Map<String, AtomicInteger> entityCounters = new HashMap<>();

        for (Span span : filteredSpans) {
            String entityType = span.getType().toUpperCase();
            int count = entityCounters.computeIfAbsent(entityType, k -> new AtomicInteger(0)).incrementAndGet();
            String placeholder = String.format("[[%s_%d]]", entityType, count);

            String originalValue = text.substring(span.getStart(), span.getEnd());
            placeholderMappings.put(placeholder, originalValue);

            anonymizedTextBuilder.replace(span.getStart(), span.getEnd(), placeholder);
        }

        return new AnonymizationResult(anonymizedTextBuilder.toString(), placeholderMappings);
    }

    private List<Span> filterOverlappingSpans(List<Span> spans) {
        if (spans.isEmpty()) {
            return Collections.emptyList();
        }

        spans.sort(Comparator.comparingInt(Span::getStart).thenComparingInt(s -> s.getEnd() - s.getStart(), Comparator.reverseOrder()));

        List<Span> filteredSpans = new ArrayList<>();
        filteredSpans.add(spans.get(0));

        for (int i = 1; i < spans.size(); i++) {
            Span current = spans.get(i);
            Span lastAdded = filteredSpans.get(filteredSpans.size() - 1);

            if (current.getStart() >= lastAdded.getEnd()) {
                filteredSpans.add(current);
            }
        }
        return filteredSpans;
    }
}
