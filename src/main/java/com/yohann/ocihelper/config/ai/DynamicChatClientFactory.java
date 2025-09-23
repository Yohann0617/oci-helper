package com.yohann.ocihelper.config.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * @ClassName DynamicChatClientFactory
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-09-23 14:42
 **/
@Component
public class DynamicChatClientFactory {
    public ChatClient create(String apiKey, String baseUrl, String model) {
        // 构建 ChatClient
        return ChatClient.builder(OpenAiChatModel.builder()
                        .openAiApi(OpenAiApi.builder()
                                .apiKey(apiKey)
                                .baseUrl(baseUrl)
                                .build())
                        .defaultOptions(OpenAiChatOptions.builder()
                                .model(model)
                                .build())
                        .build())
                .build();
    }
}