package com.docanalyzer.rag;

import java.util.ArrayList;
import java.util.List;

public class DocumentSplitter {

    private final int chunkSize;
    private final int chunkOverlap;

    public DocumentSplitter(int chunkSize, int chunkOverlap) {
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("Overlap must be smaller than chunk size.");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }
        List<String> chunks = new ArrayList<>();
        splitRecursive(text, chunks);
        return chunks;
    }

    private void splitRecursive(String text, List<String> chunks) {
        int textLength = text.length();
        if (textLength == 0) {
            return;
        }

        if (textLength <= chunkSize) {
            chunks.add(text);
            return;
        }

        // Take the first chunk
        String chunk = text.substring(0, chunkSize);
        chunks.add(chunk);

        // Get the rest of the text to process, with overlap
        int nextStart = chunkSize - chunkOverlap;

        String remainingText = text.substring(nextStart);

        splitRecursive(remainingText, chunks);
    }
}
