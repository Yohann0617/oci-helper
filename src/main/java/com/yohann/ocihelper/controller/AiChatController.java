package com.yohann.ocihelper.controller;

import com.yohann.ocihelper.config.ai.DynamicChatClientFactory;
import com.yohann.ocihelper.utils.search.DuckDuckGoSearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * @ClassName AIChatController
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-09-22 14:02
 **/
@RestController
@RequestMapping("/chat")
@Slf4j
public class AiChatController {

    private final ChatClient chatClient;
    private final DuckDuckGoSearchService searchService;

    public AiChatController(DynamicChatClientFactory factory,
                            DuckDuckGoSearchService searchService) {
        // 动态配置
        String apiKey = "sk-aehhmqwjfwusxyevalxpdstmugoeziewieaghfcwfmphwvxf";
        String baseUrl = "https://api.siliconflow.cn";
        String model = "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B";

        this.chatClient = factory.create(apiKey, baseUrl, model);
        this.searchService = searchService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamSse(@RequestParam("message") String message,
                                  @RequestParam("model") String model) {
        try {
            return chatClient
                    .prompt()
                    .options(OpenAiChatOptions.builder()
                            .model(StringUtils.isBlank(model) ? "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B" : model)
                            .build())
                    .user(message)
                    .stream()
                    .content()
                    .map(chunk -> chunk == null ? "" : chunk.toString())
                    .bufferUntil(chunk -> chunk.endsWith("。") || chunk.endsWith("\n") || chunk.endsWith("."))
                    .map(list -> String.join("", list))
                    .onErrorResume(error -> {
                        // 错误处理
                        log.error("Stream error: ", error);
                        return Flux.just("抱歉，处理您的请求时出现了错误。");
                    });
        } catch (Exception e) {
            log.error("Chat error: ", e);
            return Flux.just("抱歉，服务暂时不可用。");
        }
    }

    //    @PostMapping(value = "/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
//    public Flux<String> stream(@RequestBody Map<String, String> request) {
//        String userMessage = request.get("message");
//
//        return chatClient
//                .prompt()
//                .user(userMessage)
//                .stream()
//                .content()  // Flux<String> 每个 chunk 太小
//                .bufferUntil(chunk -> chunk.endsWith("。") || chunk.endsWith("\n")) // 合并小 chunk
//                .map(list -> String.join("", list)); // 返回较长 chunk
//    }

    //    @PostMapping(value = "/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
//    public Flux<String> streamWithSearch(@RequestBody Map<String, String> request) {
//        String userMessage = request.get("message");
//
//        return searchService.search(userMessage)
//                .flatMapMany(results -> {
//                    System.out.println("------->"+results);
//                    String prompt = userMessage + "\n根据以下内容回答：\n" +
//                            String.join("\n", results);
//
//                    return chatClient.prompt()
//                            .user(prompt)
//                            .stream()
//                            .content()
//                            .bufferUntil(chunk -> chunk.endsWith("。") || chunk.endsWith("\n"))
//                            .map(list -> String.join("", list));
//                });
//    }

}
