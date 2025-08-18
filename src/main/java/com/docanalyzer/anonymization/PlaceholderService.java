package com.docanalyzer.anonymization;

import com.docanalyzer.anonymization.deepseek.DeepseekAnonymizationProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class PlaceholderService {

    private final PlaceholderMappingRepository mappingRepository;

    @Inject
    public PlaceholderService(PlaceholderMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    @Transactional
    public String savePlaceholders(AnonymizationProvider.AnonymizationResult result , String chatSessionId) {
        result.getMappings().forEach((placeholder, originalValue) -> {
            PlaceholderMapping mapping = new PlaceholderMapping();
            mapping.setChatSessionId(chatSessionId);
            mapping.setPlaceholder(placeholder);
            mapping.setOriginalValue(originalValue);
            mappingRepository.persist(mapping);
        });

        return result.getAnonymizedText();
    }

}
