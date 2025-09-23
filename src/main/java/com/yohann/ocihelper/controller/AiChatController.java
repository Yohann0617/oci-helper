package com.yohann.ocihelper.controller;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yohann.ocihelper.bean.entity.OciKv;
import com.yohann.ocihelper.config.ai.DynamicChatClientFactory;
import com.yohann.ocihelper.enums.SysCfgEnum;
import com.yohann.ocihelper.service.IOciKvService;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import com.yohann.ocihelper.utils.search.DuckDuckGoSearchService;
import jakarta.annotation.Resource;
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

    @Resource
    private CustomExpiryGuavaCache<String, Object> customCache;

    private final DynamicChatClientFactory factory;
    private final DuckDuckGoSearchService searchService;

    public AiChatController(DynamicChatClientFactory factory,
                            DuckDuckGoSearchService searchService) {
        this.factory = factory;
        this.searchService = searchService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamSse(@RequestParam("message") String message,
                                  @RequestParam("model") String model) {
        String apiKey;
        try {
            apiKey = (String) customCache.get(SysCfgEnum.SILICONFLOW_AI_API.getCode());
            if (StringUtils.isBlank(apiKey)) {
                IOciKvService kvService = SpringUtil.getBean(IOciKvService.class);
                OciKv cfg = kvService.getOne(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, SysCfgEnum.SILICONFLOW_AI_API.getCode()));
                if (null == cfg || StringUtils.isBlank(cfg.getValue())) {
                    return Flux.just("抱歉，您未配置API秘钥，服务暂时不可用。");
                } else {
                    apiKey = cfg.getValue();
                    customCache.put(SysCfgEnum.SILICONFLOW_AI_API.getCode(), apiKey, 24 * 60 * 60 * 1000);
                }
            }
            String baseUrl = "https://api.siliconflow.cn";
            String defaultModel = "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B";

            ChatClient chatClient = factory.create(apiKey, baseUrl, StringUtils.isBlank(model) ? defaultModel : model);
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
