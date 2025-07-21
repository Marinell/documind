package com.docanalyzer.chat;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
interface DocumentAssistant {

    @SystemMessage("""
        You are an expert document assistant, specialized in the business, financial, tax and legal sector.
        Your task is to analyze the provided document text.
        Focus on identifying key information.
        Do not mention that you are an AI. Response in markdown format. If you don't know the answer, say so.
        The document to analyze is the following: {document}
        """)
    String chat(@UserMessage String message, String document);
}
