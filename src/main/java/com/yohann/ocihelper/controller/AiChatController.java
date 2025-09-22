package com.yohann.ocihelper.controller;

import com.yohann.ocihelper.utils.search.DuckDuckGoSearchService;
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
    private final DuckDuckGoSearchService searchService;

    public AiChatController(ChatClient.Builder chatClientBuilder,
                            DuckDuckGoSearchService searchService) {
        this.chatClient = chatClientBuilder.build();
        this.searchService = searchService;
    }

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
