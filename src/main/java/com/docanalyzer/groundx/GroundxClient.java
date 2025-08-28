package com.docanalyzer.groundx;

import com.docanalyzer.groundx.model.CreateBucketRequest;
import com.docanalyzer.groundx.model.CreateBucketResponse;
import com.docanalyzer.groundx.model.IngestResponse;
import com.docanalyzer.groundx.model.ProcessingStatusResponse;
import com.docanalyzer.groundx.model.SearchRequest;
import com.docanalyzer.groundx.model.SearchResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.MultipartForm;
import org.jboss.resteasy.reactive.PartType;

import java.io.File;

@Path("/api/v1")
@RegisterRestClient(configKey="groundx-api")
public interface GroundxClient {

    @POST
    @Path("/buckets")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    CreateBucketResponse createBucket(CreateBucketRequest request, @HeaderParam("x-api-key") String apiKey);

    @POST
    @Path("/ingest")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    IngestResponse ingest(@MultipartForm IngestForm data, @HeaderParam("x-api-key") String apiKey);

    class IngestForm {
        @FormParam("bucketId")
        @PartType(MediaType.TEXT_PLAIN)
        public int bucketId;

        @FormParam("file")
        @PartType(MediaType.APPLICATION_OCTET_STREAM)
        public File file;

        @FormParam("fileName")
        @PartType(MediaType.TEXT_PLAIN)
        public String fileName;

        @FormParam("fileType")
        @PartType(MediaType.TEXT_PLAIN)
        public String fileType;
    }

    @GET
    @Path("/documents/{processId}/status")
    @Produces(MediaType.APPLICATION_JSON)
    ProcessingStatusResponse getProcessingStatus(@PathParam("processId") String processId, @HeaderParam("x-api-key") String apiKey);

    @POST
    @Path("/buckets/{bucketId}/search")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    SearchResponse searchContent(@PathParam("bucketId") int bucketId, SearchRequest request, @HeaderParam("x-api-key") String apiKey);
}
