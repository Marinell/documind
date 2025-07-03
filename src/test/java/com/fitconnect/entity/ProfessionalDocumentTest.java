package com.fitconnect.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProfessionalDocumentTest {

    @Test
    public void testStoragePathGetterSetter() {
        ProfessionalDocument document = new ProfessionalDocument();
        String expectedStoragePath = "/test/path/to/document.pdf";
        document.setStoragePath(expectedStoragePath);
        assertEquals(expectedStoragePath, document.getStoragePath());
    }
}
