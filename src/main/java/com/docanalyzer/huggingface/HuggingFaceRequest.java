package com.docanalyzer.huggingface;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HuggingFaceRequest {
    private List<Message> messages;
    private String model;
    private boolean stream = false;
}
