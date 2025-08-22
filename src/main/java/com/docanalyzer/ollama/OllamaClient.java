package com.docanalyzer.ollama;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(baseUri = "http://localhost:11434")
public interface OllamaClient {

    @POST
    @Path("/api/generate")
    @Consumes(MediaType.APPLICATION_JSON)
    OllamaResponse generate(OllamaRequest request);
}
