package com.docanalyzer.chat;

public class ChatServiceException extends RuntimeException {

    public ChatServiceException(String message) {
        super(message);
    }

    public ChatServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
