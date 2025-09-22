package com.yohann.ocihelper.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * @ClassName AIChatController
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-09-22 14:02
 **/
@RestController
@RequestMapping("/chat")
public class AiChatController {

    private final ChatClient chatClient;

    public AiChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 流式聊天接口，前端可通过 fetch + ReadableStream 调用
     */
    @PostMapping(value = "/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<String> stream(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");

        return chatClient
                .prompt()
                .user(userMessage)
                .stream()
                .content()  // Flux<String> 每个 chunk 太小
                .bufferUntil(chunk -> chunk.endsWith("。") || chunk.endsWith("\n")) // 合并小 chunk
                .map(list -> String.join("", list)); // 返回较长 chunk
    }
}
