package com.docanalyzer.anonymization;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
// Potentially add import jakarta.enterprise.inject.Default; if needed, or other qualifiers

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class AnonymizationService {

    private final AnonymizationProvider anonymizationProvider;
    private final PlaceholderMappingRepository mappingRepository;

    @Inject
    public AnonymizationService(AnonymizationProvider anonymizationProvider,
                                PlaceholderMappingRepository mappingRepository) {
        this.anonymizationProvider = anonymizationProvider;
        this.mappingRepository = mappingRepository;
    }

    /**
     * Anonymizes the document content for a given chat session.
     * The placeholder-to-original_value mappings are stored in the database.
     *
     * @param originalDocument The document text to anonymize.
     * @param chatSessionId The ID of the chat session.
     * @return The anonymized document text.
     */
    @Transactional
    public String anonymizeDocument(String originalDocument, String chatSessionId) {
        if (originalDocument == null || originalDocument.isBlank()) {
            return originalDocument;
        }

        AnonymizationProvider.AnonymizationResult result = anonymizationProvider.anonymize(originalDocument, chatSessionId);

        result.getMappings().forEach((placeholder, originalValue) -> {
            PlaceholderMapping mapping = new PlaceholderMapping();
            mapping.setChatSessionId(chatSessionId);
            mapping.setPlaceholder(placeholder);
            mapping.setOriginalValue(originalValue);
            mappingRepository.persist(mapping);
        });

        return result.getAnonymizedText();
    }

    /**
     * De-anonymizes a response from the LLM using the stored mappings for a chat session.
     *
     * @param anonymizedResponse The response text from the LLM possibly containing placeholders.
     * @param chatSessionId The ID of the chat session.
     * @return The de-anonymized response text.
     */
    @Transactional
    public String deanonymizeResponse(String anonymizedResponse, String chatSessionId) {
        if (anonymizedResponse == null || anonymizedResponse.isBlank()) {
            return anonymizedResponse;
        }

        List<PlaceholderMapping> mappings = mappingRepository.findByChatSessionId(chatSessionId);
        if (mappings.isEmpty()) {
            return anonymizedResponse; // No mappings for this session
        }

        // A simple string replacement. For more complex scenarios (e.g., overlapping placeholders),
        // a more sophisticated approach might be needed.
        String deAnonymizedText = anonymizedResponse;
        for (PlaceholderMapping mapping : mappings) {
            // Ensure placeholder is replaced correctly, avoid replacing parts of other words
            // or already replaced placeholders. Using Pattern.quote for literal replacement.
            deAnonymizedText = deAnonymizedText.replace(mapping.getPlaceholder(), mapping.getOriginalValue());
        }

        return deAnonymizedText;
    }

    /**
     * Clears all anonymization mappings for a specific chat session.
     * This should be called when a "New Chat" is initiated.
     *
     * @param chatSessionId The ID of the chat session whose mappings are to be cleared.
     */
    @Transactional
    public void clearMappingsForSession(String chatSessionId) {
        mappingRepository.delete("chatSessionId", chatSessionId);
    }

    /**
     * Placeholder for a real AnonymizationProvider (e.g., DeepseekAnonymizationProvider).
     * This dummy implementation does not actually anonymize but provides the structure.
     */
    @ApplicationScoped // Make this a CDI bean
    private static class DummyAnonymizationProvider implements AnonymizationProvider {
        private int counter = 1;
        private static final Pattern WORD_PATTERN = Pattern.compile("\\b([A-Z][a-z]+|[A-Z]+)\\b"); // Simple pattern for names/acronyms

        @Override
        public AnonymizationResult anonymize(String text, String chatId) {
            // WARNING: This is a very naive dummy anonymizer.
            // It just replaces capitalized words with placeholders.
            // This is NOT suitable for real anonymization.
            // A real implementation would call Deepseek or a similar service.
            StringBuffer anonymizedText = new StringBuffer();
            Matcher matcher = WORD_PATTERN.matcher(text);
            Map<String, String> mappings = new java.util.HashMap<>();

            while (matcher.find()) {
                String originalValue = matcher.group(1);
                // Avoid re-mapping if already seen (simple check)
                if (mappings.containsValue(originalValue)) {
                    // find existing placeholder
                    String placeholder = mappings.entrySet().stream()
                            .filter(entry -> entry.getValue().equals(originalValue))
                            .findFirst()
                            .map(Map.Entry::getKey)
                            .orElse("[UNKNOWN_" + (counter++) + "]"); // Should not happen if logic is correct
                    matcher.appendReplacement(anonymizedText, Matcher.quoteReplacement(placeholder));
                } else {
                    String placeholder = "[" + chatId.substring(0, Math.min(4, chatId.length())) + "_ENTITY_" + (counter++) + "]";
                    mappings.put(placeholder, originalValue);
                    matcher.appendReplacement(anonymizedText, Matcher.quoteReplacement(placeholder));
                }
            }
            matcher.appendTail(anonymizedText);

            System.out.println("DummyAnonymizer Mappings for " + chatId + ": " + mappings);
            System.out.println("DummyAnonymizer Text for " + chatId + ": " + anonymizedText.toString());

            return new AnonymizationResult(anonymizedText.toString(), mappings);
        }
    }
}
