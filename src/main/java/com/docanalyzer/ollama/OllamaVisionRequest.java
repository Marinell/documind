package com.docanalyzer.ollama;

import java.util.List;

public record OllamaVisionRequest(String model, String prompt, List<String> images) {
}
