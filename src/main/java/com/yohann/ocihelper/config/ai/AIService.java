package com.yohann.ocihelper.config.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * @ClassName AIService
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-09-23 14:55
 **/
@Service
public class AIService {

    private final DynamicChatClientFactory factory;

    public AIService(DynamicChatClientFactory factory) {
        this.factory = factory;
    }

    public String chat(String message, String apiKey, String baseUrl, String model) {
        ChatClient chatClient = factory.create(apiKey, baseUrl, model);

        return chatClient.prompt()
                .user(message)
                .call()
                .content(); // 获取结果文本
    }
}