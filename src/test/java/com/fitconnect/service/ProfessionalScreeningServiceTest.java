package com.fitconnect.service;

import com.fitconnect.entity.Professional;
import com.fitconnect.entity.ProfessionalDocument;
import com.fitconnect.entity.ProfileStatus;
import com.fitconnect.entity.Skill;
import com.fitconnect.llm.ProfessionalProfileAnalyzer;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy; // For mocking AiServices or other CDI beans
import jakarta.inject.Inject; // Standard CDI inject
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;

@QuarkusTest
public class ProfessionalScreeningServiceTest {

    @Inject
    ProfessionalScreeningService screeningService;

    @InjectSpy // Mocks the LLM interface
    ProfessionalProfileAnalyzer mockProfileAnalyzer;

    // We need a way to set the API key for tests or mock the config property.
    // For now, tests might show warnings if API key isn't set, but service logic handles it.

    private Professional testProfessional;
    private Path tempFile;

    @BeforeEach
    @Transactional // Needed if test setup involves DB operations
    void setUp() throws IOException {
        // Clean up existing test data to avoid conflicts if tests run multiple times
        Professional.deleteAll(); // Be careful with deleteAll in real apps; use specific cleanup
        Skill.deleteAll();

        testProfessional = new Professional();
        testProfessional.setName("Dr. Fit");
        testProfessional.setEmail("drfit-" + System.currentTimeMillis() + "@example.com");
        testProfessional.setPassword("securepassword"); // Not directly used by screening
        testProfessional.profession = "Fitness Guru";
        testProfessional.yearsOfExperience = 10;
        testProfessional.qualifications = "PhD in Kinesiology, Certified Super Trainer";
        testProfessional.aboutYou = "Dedicated to making the world fitter, one person at a time.";
        testProfessional.profileStatus = ProfileStatus.PENDING_VERIFICATION;

        // Create a dummy document for text extraction testing
        tempFile = Files.createTempFile("testDoc", ".txt");
        Files.writeString(tempFile, "This is sample document content. Contains keywords like training and wellness.");

        ProfessionalDocument doc = new ProfessionalDocument();
        doc.setProfessional(testProfessional); // Link doc to professional
        doc.setFileName(tempFile.getFileName().toString());
        doc.setFileType("text/plain");
        doc.setStoragePath(tempFile.toString());

        List<ProfessionalDocument> docs = new ArrayList<>();
        docs.add(doc);
        testProfessional.documents = docs; // Set documents on professional

        testProfessional.persist(); // Persist professional to get an ID
        // doc.persist(); // If ProfessionalDocument is not cascaded from Professional persist
    }

    // Test cleanup can be done with @AfterEach if needed, e.g., deleting temp files

    @Test
    @Transactional
    public void testScreenProfessionalProfile_Success() {
        // Mock LLM responses
        String expectedSummary = "A highly experienced Fitness Guru with a PhD, specializing in training and wellness.";
        String expectedSkillsList = "Kinesiology, Super Training, Wellness Coaching";
        Mockito.when(mockProfileAnalyzer.summarizeExpertiseAndSkills(anyString(), anyString())).thenReturn(expectedSummary);
        Mockito.when(mockProfileAnalyzer.extractSkillsList(anyString(), anyString())).thenReturn(expectedSkillsList);

        // Execute the screening
        screeningService.screenProfessionalProfile(testProfessional.id);

        // Verify results
        Professional screenedProfessional = Professional.findById(testProfessional.id);
        assertNotNull(screenedProfessional);
        assertEquals(expectedSummary, screenedProfessional.summarizedSkills);
        assertNotNull(screenedProfessional.skills);
        assertEquals(3, screenedProfessional.skills.size()); // Kinesiology, Super Training, Wellness Coaching
        assertTrue(screenedProfessional.skills.stream().anyMatch(s -> s.name.equals("Kinesiology")));
        assertTrue(screenedProfessional.skills.stream().anyMatch(s -> s.name.equals("Super Training")));
        assertTrue(screenedProfessional.skills.stream().anyMatch(s -> s.name.equals("Wellness Coaching")));

        // Verify that skills were persisted if new
        Skill s = Skill.find("name", "Kinesiology").firstResult();
        assertNotNull(s);
    }

    @Test
    @Transactional
    public void testScreenProfessionalProfile_ApiKeyNotConfigured() {
        // This test requires a way to simulate the API key being null or placeholder.
        // One way is via @TestProfile or by directly setting the config value if possible for tests.
        // The service already logs a warning. We can check for the updated summary.
        // For now, this test assumes the default behavior when key is missing (as in application.properties placeholder)

        // Temporarily "unset" API key by mocking the config (more advanced setup) or rely on service's check
        // For this test, let's assume the service's internal check for "YOUR_OPENAI_API_KEY" works.

        screeningService.screenProfessionalProfile(testProfessional.id);

        Professional screenedProfessional = Professional.findById(testProfessional.id);
        assertNotNull(screenedProfessional);
        assertEquals("LLM screening skipped: API key not configured.", screenedProfessional.summarizedSkills);
        // Skills list should ideally be empty or unchanged if screening is skipped
        assertTrue(screenedProfessional.skills == null || screenedProfessional.skills.isEmpty());
    }

    // Add more tests: e.g., no documents, document parsing errors, LLM returns empty skills, etc.
}
