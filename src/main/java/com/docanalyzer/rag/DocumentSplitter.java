package com.docanalyzer.rag;

import java.io.IOException;
import java.io.Reader;
import java.text.BreakIterator;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.io.IOException;
import java.io.Reader;
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

    public List<String> splitBySentence(Reader reader) throws IOException {
        List<String> chunks = new ArrayList<>();
        List<String> currentChunkSentences = new ArrayList<>();
        StringBuilder textBuffer = new StringBuilder();
        char[] buffer = new char[4096];
        int charsRead;

        // A list to hold sentences extracted from the buffer
        List<String> sentences = new ArrayList<>();

        while ((charsRead = reader.read(buffer)) != -1) {
            textBuffer.append(buffer, 0, charsRead);
            BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
            iterator.setText(textBuffer.toString());

            int start = iterator.first();
            int end = iterator.next();
            int lastProcessedEnd = 0;

            // Find all complete sentences in the current buffer
            while (end != BreakIterator.DONE && end < textBuffer.length()) {
                String sentence = textBuffer.substring(start, end).trim();
                if (!sentence.isEmpty()) {
                    sentences.add(sentence);
                }
                lastProcessedEnd = end;
                start = end;
                end = iterator.next();
            }
            textBuffer.delete(0, lastProcessedEnd); // Keep the partial sentence
        }

        // Add any remaining text as the last sentence
        if (!textBuffer.isEmpty()) {
            sentences.add(textBuffer.toString().trim());
        }

        // Now, process the collected sentences with the correct chunking logic
        int sentenceIndex = 0;
        while (sentenceIndex < sentences.size()) {
            String sentence = sentences.get(sentenceIndex);

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
                // Do not increment sentenceIndex, re-evaluate the current sentence
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

    public List<String> splitBySemantic(Reader reader, EmbeddingModel embeddingModel) throws IOException {
        List<String> chunks = new ArrayList<>();
        StringBuilder textBuffer = new StringBuilder();
        char[] buffer = new char[4096];
        int charsRead;

        while ((charsRead = reader.read(buffer)) != -1) {
            textBuffer.append(buffer, 0, charsRead);
        }

        String text = textBuffer.toString();
        if (text.isEmpty()) {
            return chunks;
        }

        // 1. Split the text into sentences
        List<String> sentences = new ArrayList<>();
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            sentences.add(text.substring(start, end).trim());
        }

        if (sentences.isEmpty()) {
            return chunks;
        }

        // 2. Create sentence groups and generate embeddings
        List<float[]> embeddings = new ArrayList<>();
        for (String sentence : sentences) {
            embeddings.add(embeddingModel.embed(sentence).content().vector());
        }

        // 3. Calculate cosine similarity between adjacent sentences
        List<Double> similarities = new ArrayList<>();
        for (int i = 0; i < embeddings.size() - 1; i++) {
            similarities.add(cosineSimilarity(embeddings.get(i), embeddings.get(i + 1)));
        }

        // 4. Identify significant shifts in topic
        // A simple approach: identify a drop in similarity as a potential split point.
        // A more advanced approach could use a threshold or look for local minima.
        // For this implementation, we'll use a simple threshold.
        // We'll set a threshold based on the average similarity. This is a heuristic.
        double avgSimilarity = similarities.stream().mapToDouble(d -> d).average().orElse(0.0);
        double threshold = avgSimilarity * 0.8; // 20% drop from average

        List<Integer> splitIndices = new ArrayList<>();
        for (int i = 0; i < similarities.size(); i++) {
            if (similarities.get(i) < threshold) {
                splitIndices.add(i + 1);
            }
        }

        // 5. Create chunks
        int lastSplit = 0;
        for (int splitIndex : splitIndices) {
            StringBuilder chunkBuilder = new StringBuilder();
            for (int i = lastSplit; i < splitIndex; i++) {
                chunkBuilder.append(sentences.get(i)).append(" ");
            }
            chunks.add(chunkBuilder.toString().trim());
            lastSplit = splitIndex;
        }

        // Add the last chunk
        StringBuilder lastChunkBuilder = new StringBuilder();
        for (int i = lastSplit; i < sentences.size(); i++) {
            lastChunkBuilder.append(sentences.get(i)).append(" ");
        }
        if (!lastChunkBuilder.isEmpty()) {
            chunks.add(lastChunkBuilder.toString().trim());
        }

        return chunks;
    }

    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }


}
