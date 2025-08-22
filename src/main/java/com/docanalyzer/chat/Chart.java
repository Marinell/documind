package com.docanalyzer.chat;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Chart {
    private String chartType;
    private String title;
    private List<String> labels;
    private List<Dataset> datasets;


    @Getter
    @Setter
    public static class Dataset {
        private String label;
        private List<Double> data;
    }
}
