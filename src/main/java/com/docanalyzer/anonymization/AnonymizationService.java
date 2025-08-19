package com.docanalyzer.anonymization;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
@Slf4j
public class AnonymizationService {

    private final AnonymizationProvider anonymizationProvider;
    private final PlaceholderService placeholderService;
    private final PlaceholderMappingRepository mappingRepository;

    @Inject
    public AnonymizationService(AnonymizationProvider anonymizationProvider,
                                PlaceholderMappingRepository mappingRepository,
                                PlaceholderService placeholderService
    ) {
        this.anonymizationProvider = anonymizationProvider;
        this.mappingRepository = mappingRepository;
        this.placeholderService = placeholderService;
    }

    /**
     * Anonymizes the document content for a given chat session.
     * The placeholder-to-original_value mappings are stored in the database.
     *
     * @param originalDocument The document text to anonymize.
     * @param chatSessionId The ID of the chat session.
     * @return The anonymized document text.
     */
    public String anonymizeDocument(String originalDocument, String chatSessionId) {
        if (originalDocument == null || originalDocument.isBlank()) {
            return originalDocument;
        }

        AnonymizationProvider.AnonymizationResult result = anonymizationProvider.anonymize(originalDocument, chatSessionId);

        placeholderService.savePlaceholders(result, chatSessionId);

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

        log.info("anonymizedResponse: " + anonymizedResponse);

        List<PlaceholderMapping> mappings = mappingRepository.findByChatSessionId(chatSessionId);
        if (mappings.isEmpty()) {
            log.info("NO PLACEHOLDERS MAPPINGS");
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

}
