package com.aiagent.chatagent.controller;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiagent.chatagent.Monitor.MonitorContext;
import com.aiagent.chatagent.Monitor.MonitorContextHolder;
import com.aiagent.chatagent.ai.AiChat;
import com.aiagent.chatagent.model.dto.ChatRequest;
import com.aiagent.chatagent.model.dto.KnowledgeRequest;
import com.aiagent.chatagent.rag.SourceInfo;
import com.aiagent.chatagent.rag.SourceTrackingContentRetriever;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@Slf4j
public class AiChatController {

    @Resource
    private AiChat aiChat;

    @Resource
    private EmbeddingStoreIngestor embeddingStoreIngestor;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${rag.docs-path}")
    private String docsPath;

    private static final String UI_MSG_KEY_PREFIX = "ui-msgs:";
    private static final Duration UI_MSG_TTL = Duration.ofHours(2);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String TARGET_FILENAME = "ChatAgent.md";

    @PostConstruct
    public void checkRedisConnection() {
        try {
            String pong = stringRedisTemplate.getConnectionFactory().getConnection().ping();
            log.info("Redis 连接状态: {} (UI消息存储就绪)", pong);
        } catch (Exception e) {
            log.error("Redis 连接失败，消息持久化不可用: {}", e.getMessage());
        }
    }

    @PostMapping("/chat")
    public String chat(@RequestBody ChatRequest chatRequest,
                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            userId = 0L;
        }
        String prompt = chatRequest.getPrompt();
        Long sessionId = chatRequest.getSessionId();

        appendUiMessage(sessionId, "user", prompt, null);

        MonitorContextHolder.setContext(MonitorContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build());
        String chat = aiChat.chat(sessionId, prompt);
        MonitorContextHolder.clearContext();

        appendUiMessage(sessionId, "ai", chat, null);
        return chat;
    }

    @PostMapping("/streamChat")
    public Flux<String> streamChat(@RequestBody ChatRequest chatRequest,
                                   HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            userId = 0L;
        }
        String prompt = chatRequest.getPrompt();
        Long sessionId = chatRequest.getSessionId();

        appendUiMessage(sessionId, "user", prompt, null);

        MonitorContext context = MonitorContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();

        StringBuilder fullResponse = new StringBuilder();

        return Flux.defer(() -> {
            MonitorContextHolder.setContext(context);
            Flux<String> chatStream = aiChat.streamChat(sessionId, prompt);
            List<SourceInfo> sources = SourceTrackingContentRetriever.getAndClearSources();

            // 将要存入 Redis 的 sources（在 lambda 外捕获）
            final List<SourceInfo> capturedSources = (sources != null && !sources.isEmpty())
                    ? new ArrayList<>(sources) : null;

            Flux<String> resultStream;
            if (capturedSources != null) {
                try {
                    String sourcesJson = objectMapper.writeValueAsString(capturedSources);
                    Flux<String> sourcesFlux = Flux.just("__SOURCES__:" + sourcesJson + "\n");
                    resultStream = Flux.concat(sourcesFlux, chatStream);
                } catch (Exception e) {
                    log.warn("序列化 RAG 来源失败", e);
                    resultStream = chatStream;
                }
            } else {
                resultStream = chatStream;
            }

            return resultStream
                    .doOnNext(token -> {
                        if (!token.startsWith("__SOURCES__:")) {
                            fullResponse.append(token);
                        }
                    })
                    .doFinally(signal -> {
                        appendUiMessage(sessionId, "ai", fullResponse.toString(), capturedSources);
                        MonitorContextHolder.clearContext();
                    });
        });
    }

    @GetMapping("/messages")
    public List<Map<String, Object>> getMessages(@RequestParam("sessionId") Long sessionId) {
        String key = UI_MSG_KEY_PREFIX + sessionId;
        log.debug("查询 UI 消息 key: {}", key);
        List<String> jsonList = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (jsonList == null || jsonList.isEmpty()) {
            log.debug("key {} 无消息", key);
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String json : jsonList) {
            try {
                Map<String, Object> msg = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                result.add(msg);
            } catch (Exception e) {
                log.warn("反序列化 UI 消息失败: {}", e.getMessage());
            }
        }
        log.debug("key {} 返回 {} 条消息", key, result.size());
        return result;
    }

    private void appendUiMessage(Long sessionId, String role, String text, List<SourceInfo> sources) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("role", role);
            msg.put("text", text);
            msg.put("ts", System.currentTimeMillis());
            if (sources != null && !sources.isEmpty()) {
                msg.put("sources", sources);
            }
            String key = UI_MSG_KEY_PREFIX + sessionId;
            String json = objectMapper.writeValueAsString(msg);
            stringRedisTemplate.opsForList().rightPush(key, json);
            stringRedisTemplate.expire(key, UI_MSG_TTL);
        } catch (Exception e) {
            log.error("存储 UI 消息到 Redis 失败: {}", e.getMessage(), e);
        }
    }

    @PostMapping("/insert")
    public String insertKnowledge(@RequestBody KnowledgeRequest knowledgeRequest) {
        String formattedContent = String.format("### Q：%s\n\nA：%s", knowledgeRequest.getQuestion(), knowledgeRequest.getAnswer());

        boolean writeSuccess = appendToFile(formattedContent, knowledgeRequest.getSourceName());
        if (!writeSuccess) {
            return "插入失败：无法写入本地文件";
        }

        try {
            String sourceName = (knowledgeRequest.getSourceName() != null) ? knowledgeRequest.getSourceName() : TARGET_FILENAME;
            Metadata metadata = Metadata.from("file_name", sourceName);

            Document document = Document.from(formattedContent, metadata);
            embeddingStoreIngestor.ingest(document);

            log.info("RAG - 新增知识点成功: {}", knowledgeRequest.getQuestion());
            return "插入成功：已同步至 " + knowledgeRequest.getSourceName() + " 及向量数据库";
        } catch (Exception e) {
            log.error("RAG - 向量化失败", e);
            return "插入部分成功：文件已写入，但向量库更新失败";
        }
    }

    private synchronized boolean appendToFile(String content, String sourceName) {
        try {
            Path filePath = Paths.get(docsPath, sourceName);
            log.info("文件实际写入位置: {}", filePath.toAbsolutePath());
            if (!Files.exists(filePath)) {
                Files.createDirectories(filePath.getParent());
                Files.createFile(filePath);
            }

            String textToAppend = "\n\n" + content;

            Files.writeString(
                    filePath,
                    textToAppend,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.CREATE
            );
            return true;
        } catch (IOException e) {
            log.error("RAG - 写入本地文件失败: {}", e.getMessage(), e);
            return false;
        }
    }
}
