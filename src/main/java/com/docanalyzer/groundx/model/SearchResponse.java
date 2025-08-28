package com.docanalyzer.groundx.model;

import java.util.List;

public class SearchResponse {
    public Search search;

    public static class Search {
        public List<Result> results;
    }

    public static class Result {
        public String text;
    }
}
