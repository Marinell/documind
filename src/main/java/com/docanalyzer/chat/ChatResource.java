package com.docanalyzer.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Path("/api/chat")
@ApplicationScoped
public class ChatResource {

    private static final Logger LOG = Logger.getLogger(ChatResource.class);
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ObjectMapper objectMapper = new ObjectMapper(); // For SSE


    @Inject
    ChatService chatService;

    @POST
    @Path("/new")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Start a new chat session", description = "Creates a new unique session ID for chat and document processing.")
    public Response startNewSession() {
        String sessionId = chatService.createNewChatSession();
        LOG.infof("New chat session started: %s", sessionId);
        return Response.ok(Collections.singletonMap("sessionId", sessionId)).build();
    }

    @DELETE
    @Path("/{sessionId}")
    @Operation(summary = "Clear chat session", description = "Clears all data associated with a chat session, including documents and anonymization mappings.")
    public Response clearSession(@PathParam("sessionId") String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Session ID cannot be empty").build();
        }
        chatService.clearChatSession(sessionId);
        LOG.infof("Chat session cleared: %s", sessionId);
        return Response.ok().build();
    }

    @POST
    @Path("/{sessionId}/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Upload a document", description = "Uploads a document (PDF or TXT) to the specified chat session for processing.")
    public Response uploadDocument(@PathParam("sessionId") String sessionId,
                                   @RestForm("file") FileUpload fileUpload) {
        if (sessionId == null || sessionId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Session ID cannot be empty").build();
        }
        if (fileUpload == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("No file uploaded.").build();
        }

        LOG.infof("Received file upload for session %s: %s, type: %s, size: %d",
                sessionId, fileUpload.fileName(), fileUpload.contentType(), fileUpload.size());

        try (InputStream fileStream = Files.newInputStream(fileUpload.uploadedFile())) {
            chatService.ingestDocument(sessionId, fileStream, fileUpload.fileName());
            return Response.ok(Collections.singletonMap("message", "File uploaded and processing started successfully for " + fileUpload.fileName())).build();
        } catch (ChatServiceException e) {
            LOG.errorf(e, "A chat service error occurred for session %s: %s", sessionId, e.getMessage());
            // Return a 400 Bad Request for client-side errors (e.g., config missing)
            return Response.status(Response.Status.BAD_REQUEST).entity(Collections.singletonMap("error", e.getMessage())).build();
        } catch (IllegalStateException e) {
            LOG.errorf(e, "Error processing upload for session %s: Session not found or not initialized.", sessionId);
            return Response.status(Response.Status.NOT_FOUND).entity(Collections.singletonMap("error", e.getMessage())).build();
        } catch (IOException e) {
            LOG.errorf(e, "Error processing upload for session %s", sessionId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Collections.singletonMap("error", "Failed to process file: " + e.getMessage())).build();
        } catch (Exception e) {
            LOG.errorf(e, "Unexpected error during upload for session %s", sessionId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Collections.singletonMap("error", "An unexpected error occurred: " + e.getMessage())).build();
        }
    }

    @POST
    @Path("/{sessionId}/message")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Operation(summary = "Send a message to the chat", description = "Sends a user message to the chat session and streams the LLM's response.")
    public StreamingOutput sendMessage(@PathParam("sessionId") String sessionId,
                                       @RequestBody(
                                            description = "Message from the user",
                                            required = true,
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                                                               schema = @Schema(implementation = UserMessage.class))
                                       ) UserMessage userMessage) {
        if (sessionId == null || sessionId.isBlank()) {
            // Cannot throw WebApplicationException directly in lambda for StreamingOutput
            // Handle by sending an error event or closing stream.
            // For simplicity, logging and returning an empty stream or error event.
            LOG.error("Session ID is missing for sendMessage.");
            return output -> {
                output.write("event: error\n".getBytes());
                output.write(("data: {\"error\": \"Session ID is missing\"}\n\n").getBytes());
                output.flush();
            };
        }
        if (userMessage == null || userMessage.message == null || userMessage.message.isBlank()) {
            LOG.error("User message is missing or empty.");
            return output -> {
                output.write("event: error\n".getBytes());
                output.write(("data: {\"error\": \"User message is missing or empty\"}\n\n").getBytes());
                output.flush();
            };
        }

        LOG.infof("Received message for session %s: %s", sessionId, userMessage.message);

        return output -> {
            try {
                chatService.streamChatResponse(sessionId, userMessage.message,
                    eventMap -> { // eventMap is Map<String, Object>
                        try {
                            String eventType = (String) eventMap.getOrDefault("type", "message"); // Default type
                            String jsonData = objectMapper.writeValueAsString(eventMap.get("data"));

                            // Send specific event type if it's a chart, otherwise default SSE message event
                            if ("chart".equals(eventType)) {
                                output.write("event: chart\n".getBytes());
                            }
                            // For tokens, no explicit event name, just data (standard SSE 'message' event)
                            // output.write("event: token\n".getBytes()); // Could also do this

                            output.write(("data: " + jsonData + "\n\n").getBytes());
                            output.flush();
                        } catch (JsonProcessingException e) {
                            LOG.errorf(e, "Error serializing event data to JSON for session %s", sessionId);
                            // Potentially send an error event to client
                        } catch (IOException e) {
                            LOG.errorf(e, "IOException while streaming event to client for session %s", sessionId);
                            throw new RuntimeException("Client disconnected or stream broken", e);
                        }
                    },
                    onComplete -> {
                        try {
                            output.write("event: complete\n".getBytes());
                            output.write(("data: {\"message\": \"Stream finished\"}\n\n").getBytes());
                            output.flush();
                            LOG.infof("Stream completed for session %s", sessionId);
                        } catch (IOException e) {
                            LOG.errorf(e, "IOException while sending completion event for session %s", sessionId);
                        } finally {
                            try {
                                output.close();
                            } catch (IOException e) {
                                LOG.warnf(e, "Error closing SSE stream for session %s", sessionId);
                            }
                        }
                    },
                    onError -> {
                        try {
                            output.write("event: error\n".getBytes());
                            // Sanitize error message before sending to client
                            String clientError = "An error occurred during chat processing.";
                            if (onError instanceof IllegalStateException) {
                                clientError = onError.getMessage(); // Safe to pass some specific errors
                            }
                            output.write(("data: {\"error\": \"" + clientError.replace("\"", "\\\"") + "\"}\n\n").getBytes());
                            output.flush();
                            LOG.errorf(onError, "Error event sent to client for session %s", sessionId);
                        } catch (IOException e) {
                            LOG.errorf(e, "IOException while sending error event for session %s", sessionId);
                        } finally {
                           try {
                                output.close();
                            } catch (IOException e) {
                                LOG.warnf(e, "Error closing SSE stream after error for session %s", sessionId);
                            }
                        }
                    });
            } catch (Exception e) {
                LOG.errorf(e, "Unhandled exception in StreamingOutput for session %s", sessionId);
                 try {
                    output.write("event: error\n".getBytes());
                    output.write(("data: {\"error\": \"Failed to initiate chat stream.\"}\n\n").getBytes());
                    output.flush();
                    output.close();
                } catch (IOException ex) {
                    LOG.warnf(ex, "Could not send final error to client for session %s", sessionId);
                }
            }
        };
    }

    // Simple DTO for user messages
    public static class UserMessage {
        public String message;
    }
}
