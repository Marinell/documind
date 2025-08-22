package com.docanalyzer.rag;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DocumentSplitter {

    private final int chunkSize;
    private final int chunkOverlap;

    public DocumentSplitter(int chunkSize, int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public List<String> split(String text) {
        List<String> chunks = Arrays.stream(text.split("\\r?\\n"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        // For now, a simple split by newline.
        // A more advanced implementation would handle chunk size and overlap.
        return chunks;
    }
}
