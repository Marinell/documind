package com.docanalyzer.groundx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IngestResponse {
    public Ingest ingest;

    public static class Ingest {
        @JsonProperty("processId")
        public String processId;
    }
}
