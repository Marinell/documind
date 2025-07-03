package com.docanalyzer.anonymization;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "placeholder_mappings",
       indexes = {
           @Index(name = "idx_session_placeholder", columnList = "chatSessionId, placeholder", unique = true),
           @Index(name = "idx_session_original", columnList = "chatSessionId, originalValue")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_session_placeholder", columnNames = {"chatSessionId", "placeholder"})
       }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlaceholderMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "chat_session_id")
    private String chatSessionId; // To associate mappings with a specific chat session

    @Column(nullable = false, unique = true) // Placeholder should be unique within a session, enforced by table constraint
    private String placeholder; // e.g., "[PERSON_1]", "[ADDRESS_A]"

    @Column(nullable = false, length = 1024) // Original value might be long
    private String originalValue; // e.g., "John Doe", "123 Main St"

    // Lombok will generate constructors, getters, setters
}
