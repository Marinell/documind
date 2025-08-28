package com.docanalyzer.groundx.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateBucketResponse {
    public Bucket bucket;

    public static class Bucket {
        @JsonProperty("bucketId")
        public int bucketId;
    }
}
