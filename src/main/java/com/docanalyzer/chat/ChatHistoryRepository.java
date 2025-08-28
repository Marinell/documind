package com.docanalyzer.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.list.ListCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
@Slf4j
public class ChatHistoryRepository {

    private final ListCommands<String, String> listCommands;
    private final ValueCommands<String, Integer> valueCommands;
    private final ObjectMapper objectMapper = new ObjectMapper();


    public ChatHistoryRepository(RedisDataSource redisDataSource) {
        this.listCommands = redisDataSource.list(String.class, String.class);
        this.valueCommands = redisDataSource.value(String.class, Integer.class);
    }

    public void addMessage(String sessionId, String author, String text) {
        try {
            ChatMessage message = new ChatMessage(author, text);
            String jsonMessage = objectMapper.writeValueAsString(message);
            listCommands.rpush("chat_history:" + sessionId, jsonMessage);
        } catch (JsonProcessingException e) {
            log.error("Error serializing chat message", e);
        }
    }

    public List<ChatMessage> getHistory(String sessionId) {
        List<String> jsonMessages = listCommands.lrange("chat_history:" + sessionId, 0, -1);
        return jsonMessages.stream()
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, ChatMessage.class);
                    } catch (JsonProcessingException e) {
                        log.error("Error deserializing chat message", e);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void setBucketId(String sessionId, int bucketId) {
        valueCommands.set("bucket_id:" + sessionId, bucketId);
    }

    public int getBucketId(String sessionId) {
        Integer bucketId = valueCommands.get("bucket_id:" + sessionId);
        return bucketId != null ? bucketId : 0;
    }

    public static class ChatMessage {
        public String author;
        public String text;

        public ChatMessage() {}

        public ChatMessage(String author, String text) {
            this.author = author;
            this.text = text;
        }
    }
}
