package com.docanalyzer.chat;

interface DocumentAssistant {
    dev.langchain4j.service.TokenStream chat(String message);
}
