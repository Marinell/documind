package com.fitconnect.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitconnect.dto.LoginRequest;
import com.fitconnect.dto.RegisterRequest;
import com.fitconnect.entity.Professional;
import com.fitconnect.entity.ProfessionalDocument;
import com.fitconnect.entity.User;
import com.fitconnect.entity.UserRole;
import com.fitconnect.service.AuthService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class AuthResourceTest {

    static String clientToken;
    static String professionalToken;
    static Long registeredClientId;
    // static Long registeredProfessionalId; // This can be set in the new professional registration test

    @Inject
    AuthService authService;

    @Inject
    ObjectMapper objectMapper; // For deserializing response

    private List<Path> tempFiles = new ArrayList<>();

    Path createTempFile(String prefix, String suffix, String content) throws IOException {
        Path tempFile = Files.createTempFile(prefix, suffix);
        Files.writeString(tempFile, content);
        tempFiles.add(tempFile);
        return tempFile;
    }

    @AfterEach
    void tearDown() {
        tempFiles.forEach(tempFile -> {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                System.err.println("Failed to delete temp file: " + tempFile + " (" + e.getMessage() + ")");
            }
        });
        tempFiles.clear();
    }


    @Test
    @Order(1)
    void testRegisterClient() {
        RegisterRequest clientReg = new RegisterRequest();
        clientReg.setName("Test Client User");
        String clientEmail = "client-" + UUID.randomUUID() + "@example.com";
        clientReg.setEmail(clientEmail);
        clientReg.setPassword("password123");
        clientReg.setPhoneNumber("1234567890");

        Response response = given()
            .contentType(ContentType.JSON)
            .body(clientReg)
            .when().post("/api/auth/register/client")
            .then()
            .statusCode(201)
            .body("id", notNullValue())
            .body("email", equalTo(clientEmail))
            .body("name", equalTo(clientReg.getName()))
            .body("role", equalTo(UserRole.CLIENT.name()))
            .extract().response();

        registeredClientId = response.jsonPath().getLong("id");
        assertNotNull(registeredClientId);
    }

    // Order 2: Professional Registration Success Test
    @Test
    @Order(2)
    @Transactional // Using Transactional on the test method for Panache operations
    void testRegisterProfessionalSuccess() throws IOException {
        String uniqueEmail = "prof-" + UUID.randomUUID() + "@example.com";
        String name = "Dr. Test Professional";
        String password = "securePassword123";
        String phoneNumber = "9876543210";
        String profession = "Physiotherapist";
        String address = "123 Wellness Ave, Health City";
        String postalCode = "H0H0H0";
        Integer yearsOfExperience = 10;
        String qualifications = "MPT, Certified Sports Physio";
        String aboutYou = "Dedicated to helping patients recover and achieve their fitness goals.";
        String socialMediaLinksJson = "{\"linkedin\":\"https://linkedin.com/in/testprof\", \"website\":\"https://testprof.com\"}";

        Path doc1Path = createTempFile("cv", ".pdf", "This is a test CV.");
        File doc1File = doc1Path.toFile();
        byte[] doc1Content = Files.readAllBytes(doc1Path);

        Path doc2Path = createTempFile("cert", ".png", "Test certificate content");
        File doc2File = doc2Path.toFile();
        byte[] doc2Content = Files.readAllBytes(doc2Path);

        Response response = given()
            .multiPart("name", name)
            .multiPart("email", uniqueEmail)
            .multiPart("password", password)
            .multiPart("phoneNumber", phoneNumber)
            .multiPart("profession", profession)
            .multiPart("address", address)
            .multiPart("postalCode", postalCode)
            .multiPart("yearsOfExperience", yearsOfExperience)
            .multiPart("qualifications", qualifications)
            .multiPart("aboutYou", aboutYou)
            .multiPart("socialMediaLinksJson", socialMediaLinksJson)
            .multiPart("documents", doc1File, "application/pdf")
            .multiPart("documents", doc2File, "image/png")
            .when().post("/api/auth/register/professional")
            .then()
            .statusCode(201)
            .extract().response();

        String responseBody = response.getBody().asString();
        Professional registeredProf = objectMapper.readValue(responseBody, Professional.class);

        assertEquals(name, registeredProf.getName());
        assertEquals(uniqueEmail, registeredProf.getEmail());
        assertEquals(profession, registeredProf.getProfession());
        assertEquals(UserRole.PROFESSIONAL, registeredProf.getRole());
        assertNotNull(registeredProf.getSocialMediaLinks());
        assertEquals("https://linkedin.com/in/testprof", registeredProf.getSocialMediaLinks().get("linkedin"));
        assertNotNull(registeredProf.getDocuments());
        assertEquals(2, registeredProf.getDocuments().size());

        // Check documents from response (fileContent should be null due to @JsonIgnore)
        ProfessionalDocument responseDoc1 = registeredProf.getDocuments().stream().filter(d -> d.getFileName().equals(doc1File.getName())).findFirst().orElse(null);
        assertNotNull(responseDoc1);
        assertEquals("application/pdf", responseDoc1.getFileType());
        assertNull(responseDoc1.getFileContent()); // @JsonIgnore

        ProfessionalDocument responseDoc2 = registeredProf.getDocuments().stream().filter(d -> d.getFileName().equals(doc2File.getName())).findFirst().orElse(null);
        assertNotNull(responseDoc2);
        assertEquals("image/png", responseDoc2.getFileType());
        assertNull(responseDoc2.getFileContent()); // @JsonIgnore

        // Assert Database State
        Professional profFromDb = Professional.find("email", uniqueEmail).firstResult();
        assertNotNull(profFromDb);
        assertEquals(name, profFromDb.getName());
        assertEquals(profession, profFromDb.getProfession());
        assertTrue(profFromDb.getSocialMediaLinks().containsKey("website"));

        // Fetch documents associated with the professional from DB
        // Panache.getEntityManager().refresh(profFromDb); // Ensure lazy-loaded collections are loaded if needed, or use a specific query
        List<ProfessionalDocument> docsFromDb = ProfessionalDocument.find("professional", profFromDb).list();
        assertNotNull(docsFromDb);
        assertEquals(2, docsFromDb.size());

        ProfessionalDocument dbDoc1 = docsFromDb.stream().filter(d -> d.getFileName().equals(doc1File.getName())).findFirst().orElse(null);
        assertNotNull(dbDoc1);
        assertEquals("application/pdf", dbDoc1.getFileType());
        assertArrayEquals(doc1Content, dbDoc1.getFileContent());

        ProfessionalDocument dbDoc2 = docsFromDb.stream().filter(d -> d.getFileName().equals(doc2File.getName())).findFirst().orElse(null);
        assertNotNull(dbDoc2);
        assertEquals("image/png", dbDoc2.getFileType());
        assertArrayEquals(doc2Content, dbDoc2.getFileContent());
    }

    // Order 3: Professional Registration Email Exists Test
    @Test
    @Order(3)
    @Transactional
    void testRegisterProfessionalEmailExists() throws IOException {
        String uniqueEmail = "existing-" + UUID.randomUUID() + "@example.com";
        String name = "Original Professional";

        // First, register a professional
        given()
            .multiPart("name", name)
            .multiPart("email", uniqueEmail)
            .multiPart("password", "password123")
            .multiPart("phoneNumber", "1231231234")
            .multiPart("profession", "Tester")
            // Minimal fields for first registration
            .when().post("/api/auth/register/professional")
            .then()
            .statusCode(201);

        // Attempt to register again with the same email
        given()
            .multiPart("name", "Another Professional")
            .multiPart("email", uniqueEmail) // Same email
            .multiPart("password", "newPassword")
            .multiPart("phoneNumber", "3213214321")
            .multiPart("profession", "Another Tester")
            .when().post("/api/auth/register/professional")
            .then()
            .statusCode(400)
            .body(equalTo("Email already exists"));
    }


    @Test
    @Order(4) // Adjusted order
    void testLoginClient() {
        RegisterRequest clientReg = new RegisterRequest();
        String clientEmail = "loginclient-" + UUID.randomUUID() + "@example.com";
        clientReg.setName("Login Test Client");
        clientReg.setEmail(clientEmail);
        clientReg.setPassword("clientPass");
        clientReg.setPhoneNumber("0987654321");

        given()
            .contentType(ContentType.JSON)
            .body(clientReg)
            .when().post("/api/auth/register/client")
            .then()
            .statusCode(201);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail(clientEmail);
        loginReq.setPassword("clientPass");

        Response loginResponse = given()
            .contentType(ContentType.JSON)
            .body(loginReq)
            .when().post("/api/auth/login")
            .then()
            .statusCode(200)
            .body("token", notNullValue())
            .body("email", equalTo(clientEmail))
            .body("role", equalTo(UserRole.CLIENT.name()))
            .extract().response();

        clientToken = loginResponse.jsonPath().getString("token");
        assertNotNull(clientToken);
    }

    @Test
    @Order(5) // Adjusted order
    void testLoginInvalidCredentials() {
        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail("nonexistentuser-" + UUID.randomUUID() + "@example.com");
        loginReq.setPassword("wrongpassword");

        given()
            .contentType(ContentType.JSON)
            .body(loginReq)
            .when().post("/api/auth/login")
            .then()
            .statusCode(401); // Unauthorized
    }

    @Test
    @Order(6) // Adjusted order
    void testAccessProtectedResourceWithToken() {
        RegisterRequest clientReg = new RegisterRequest();
        String clientEmail = "protectedclient-" + UUID.randomUUID() + "@example.com";
        clientReg.setName("Protected Test Client");
        clientReg.setEmail(clientEmail);
        clientReg.setPassword("protPass");
        clientReg.setPhoneNumber("1122334455");

        given().contentType(ContentType.JSON).body(clientReg).when().post("/api/auth/register/client").then().statusCode(201);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail(clientEmail);
        loginReq.setPassword("protPass");
        String token = given().contentType(ContentType.JSON).body(loginReq).when().post("/api/auth/login").then().statusCode(200).extract().path("token");

        assertNotNull(token, "Token should not be null");

        // Example: Assuming a protected endpoint like /api/users/me or similar
        // This endpoint might not exist or might require specific roles.
        // For demonstration, using the existing /api/protected/user if it's suitable
        // If /api/protected/user is for any authenticated user:
        given()
            .header("Authorization", "Bearer " + token)
            .when().get("/api/protected/user") // Make sure this endpoint exists and is correctly protected
            .then()
            .statusCode(200) // Or 403 if role mismatch, adjust as per actual protected endpoint
            .body(containsString("Hello " + clientEmail));
    }

    @Test
    @Order(7) // Adjusted order
    void testAccessProtectedResourceWithoutToken() {
        given()
            .when().get("/api/protected/user") // Make sure this endpoint exists
            .then()
            .statusCode(401); // Unauthorized
    }

    // Removed testRegisterProfessional_Simplified and testLoginProfessional_Simplified
    // as they are replaced by the more comprehensive test above or are not relevant anymore.
}
