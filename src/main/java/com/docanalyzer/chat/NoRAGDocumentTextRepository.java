package com.docanalyzer.chat;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.list.ListCommands;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@ApplicationScoped
@Slf4j
public class NoRAGDocumentTextRepository {

    private final ListCommands<String, String> listCommands;

    public NoRAGDocumentTextRepository(RedisDataSource redisDataSource) {
        this.listCommands = redisDataSource.list(String.class, String.class);
    }

    public void addText(String sessionId, String text) {
        listCommands.rpush("doc:" + sessionId, text);
    }

    public List<String> getText(String sessionId) {
        return listCommands.lrange("doc:" + sessionId, 0, -1);
    }
}
