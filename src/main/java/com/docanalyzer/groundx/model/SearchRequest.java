package com.docanalyzer.groundx.model;

public class SearchRequest {
    public String query;
    public int n = 10;
    public int verbosity = 2;

    public SearchRequest(String query) {
        this.query = query;
    }
}
