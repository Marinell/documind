package com.docanalyzer.rag;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;

public class DocumentSplitterTest {

    @Test
    public void testSplitBySemantic() throws IOException {
        // Mock EmbeddingModel
        EmbeddingModel embeddingModel = Mockito.mock(EmbeddingModel.class);

        // Define embeddings for sentences
        float[] embedding1 = new float[]{1.0f, 0.0f, 0.0f}; // "Sentence 1."
        float[] embedding2 = new float[]{0.9f, 0.1f, 0.0f}; // "Sentence 2." - similar to 1
        float[] embedding3 = new float[]{0.0f, 1.0f, 0.0f}; // "Sentence 3." - different from 1 and 2
        float[] embedding4 = new float[]{0.1f, 0.9f, 0.0f}; // "Sentence 4." - similar to 3

        // Mock the behavior of the embedding model
        Mockito.when(embeddingModel.embed(Mockito.eq("Sentence 1.")))
                .thenReturn(Response.from(Embedding.from(embedding1)));
        Mockito.when(embeddingModel.embed(Mockito.eq("Sentence 2.")))
                .thenReturn(Response.from(Embedding.from(embedding2)));
        Mockito.when(embeddingModel.embed(Mockito.eq("Sentence 3.")))
                .thenReturn(Response.from(Embedding.from(embedding3)));
        Mockito.when(embeddingModel.embed(Mockito.eq("Sentence 4.")))
                .thenReturn(Response.from(Embedding.from(embedding4)));


        // Input text
        String text = "Sentence 1. Sentence 2. Sentence 3. Sentence 4.";
        StringReader reader = new StringReader(text);

        // DocumentSplitter
        DocumentSplitter splitter = new DocumentSplitter(100, 10);

        // Perform semantic splitting
        List<String> chunks = splitter.splitBySemantic(reader, embeddingModel);

        // Verify the chunks
        assertEquals(2, chunks.size());
        assertEquals("Sentence 1. Sentence 2.", chunks.get(0));
        assertEquals("Sentence 3. Sentence 4.", chunks.get(1));
    }
}
