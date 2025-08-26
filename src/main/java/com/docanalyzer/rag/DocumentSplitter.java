package com.docanalyzer.rag;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    /**
     * split by recursive strategy.
     * @param text
     * @return
     */
    public List<String> splitByRecursion(String text) {
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

    /**
     * split based on sentence boundaries
     * @param text
     * @return
     */
    public List<String> splitBySentence(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        // 1. Split text into sentences
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }

        if (sentences.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. Group sentences into chunks
        List<String> chunks = new ArrayList<>();
        int sentenceIndex = 0;
        List<String> currentChunkSentences = new ArrayList<>();

        while (sentenceIndex < sentences.size()) {
            String sentence = sentences.get(sentenceIndex);

            // Handle sentences larger than chunkSize
            if (sentence.length() > chunkSize) {
                if (!currentChunkSentences.isEmpty()) {
                    chunks.add(String.join(" ", currentChunkSentences));
                    currentChunkSentences.clear();
                }
                chunks.add(sentence);
                sentenceIndex++;
                continue;
            }

            List<String> tempSentences = new ArrayList<>(currentChunkSentences);
            tempSentences.add(sentence);
            String tempChunk = String.join(" ", tempSentences);

            if (tempChunk.length() > chunkSize && !currentChunkSentences.isEmpty()) {
                chunks.add(String.join(" ", currentChunkSentences));

                // Overlap
                List<String> newChunkSentences = new ArrayList<>();
                int overlapLength = 0;
                for (int i = currentChunkSentences.size() - 1; i >= 0; i--) {
                    String s = currentChunkSentences.get(i);
                    if (overlapLength + s.length() + (newChunkSentences.isEmpty() ? 0 : 1) <= chunkOverlap) {
                        newChunkSentences.add(0, s);
                        overlapLength += s.length() + 1;
                    } else {
                        break;
                    }
                }
                currentChunkSentences = newChunkSentences;
                // Don't increment sentenceIndex, so the current sentence is considered for the new chunk
            } else {
                currentChunkSentences.add(sentence);
                sentenceIndex++;
            }
        }

        if (!currentChunkSentences.isEmpty()) {
            chunks.add(String.join(" ", currentChunkSentences));
        }

        return chunks;
    }

}
