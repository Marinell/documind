package com.docanalyzer.anonymization;

import java.util.Map;

/**
 * Interface for services that can anonymize text and provide mappings
 * for de-anonymization.
 */
public interface AnonymizationProvider {

    /**
     * Anonymizes the given text.
     *
     * @param text The original text to anonymize.
     * @param chatId The chat ID to associate with this anonymization request.
     * @return An AnonymizationResult containing the anonymized text and the placeholder-value mappings.
     * @throws AnonymizationException if the anonymization process fails.
     */
    AnonymizationResult anonymize(String text, String chatId) throws AnonymizationException;

    /**
     * Represents the result of an anonymization process.
     */
    class AnonymizationResult {
        private final String anonymizedText;
        private final Map<String, String> mappings; // Placeholder -> Original Value

        public AnonymizationResult(String anonymizedText, Map<String, String> mappings) {
            this.anonymizedText = anonymizedText;
            this.mappings = mappings;
        }

        public String getAnonymizedText() {
            return anonymizedText;
        }

        public Map<String, String> getMappings() {
            return mappings;
        }
    }

    /**
     * Custom exception for anonymization errors.
     */
    class AnonymizationException extends RuntimeException {
        public AnonymizationException(String message) {
            super(message);
        }

        public AnonymizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
