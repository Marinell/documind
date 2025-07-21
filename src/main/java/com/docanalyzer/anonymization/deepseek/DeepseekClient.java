package com.docanalyzer.anonymization.deepseek;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/generate")
@RegisterRestClient(configKey="deepseek-api")
public interface DeepseekClient {

    @POST
    DeepseekResponse generate(DeepseekRequest request);
}
