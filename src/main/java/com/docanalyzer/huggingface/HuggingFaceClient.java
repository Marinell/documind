package com.docanalyzer.huggingface;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.HeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/v1/chat/completions")
@RegisterRestClient(configKey = "huggingface-api")
public interface HuggingFaceClient {

    @POST
    HuggingFaceResponse createChatCompletion(HuggingFaceRequest request, @HeaderParam("Authorization") String token);
}
