package com.docanalyzer.anonymization.presidio;

import com.docanalyzer.anonymization.presidio.model.PresidioAnalyzeRequest;
import com.docanalyzer.anonymization.presidio.model.PresidioAnonymizeRequest;
import com.docanalyzer.anonymization.presidio.model.AddRecognizerRequest;
import com.docanalyzer.anonymization.presidio.model.PresidioAnonymizeResponse;
import com.docanalyzer.anonymization.presidio.model.RecognizerResult;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@Path("/")
@RegisterRestClient(configKey = "presidio-api")
public interface PresidioClient {

    @POST
    @Path("/analyze")
    List<RecognizerResult> analyze(PresidioAnalyzeRequest request);

    @POST
    @Path("/anonymize")
    PresidioAnonymizeResponse anonymize(PresidioAnonymizeRequest request);

    @POST
    @Path("/add_recognizer")
    void addRecognizer(AddRecognizerRequest request);
}
