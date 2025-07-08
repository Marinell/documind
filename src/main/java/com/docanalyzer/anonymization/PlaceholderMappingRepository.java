package com.docanalyzer.anonymization;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

/**
 * Panache Repository for PlaceholderMapping entities.
 */
@ApplicationScoped
public class PlaceholderMappingRepository implements PanacheRepositoryBase<PlaceholderMapping, Long> {

    public List<PlaceholderMapping> findByChatSessionId(String chatSessionId) {
        return list("chatSessionId", chatSessionId);
    }

    public Optional<PlaceholderMapping> findByChatSessionIdAndPlaceholder(String chatSessionId, String placeholder) {
        return find("chatSessionId = :chatSessionId and placeholder = :placeholder",
                Parameters.with("chatSessionId", chatSessionId).and("placeholder", placeholder))
                .firstResultOptional();
    }
}
