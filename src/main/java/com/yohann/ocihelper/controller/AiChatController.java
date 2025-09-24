package com.yohann.ocihelper.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yohann.ocihelper.bean.ResponseData;
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
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

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
    @Resource
    private IOciKvService kvService;

    private final DynamicChatClientFactory factory;
    private final DuckDuckGoSearchService searchService;

    public AiChatController(DynamicChatClientFactory factory,
                            DuckDuckGoSearchService searchService) {
        this.factory = factory;
        this.searchService = searchService;
    }

    @GetMapping(value = "/removeCache")
    public ResponseData<Void> removeCache(@RequestParam("sessionId") String sessionId) {
        customCache.remove(sessionId);
        return ResponseData.successData("会话内容已清除");
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamSse(@RequestParam("message") String message,
                                  @RequestParam("model") String model,
                                  @RequestParam("sessionId") String sessionId,
                                  @RequestParam("enableInternet") Boolean enableInternet) {
        String baseUrl = "https://api.siliconflow.cn";
        String defaultModel = "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B";
        String apiKey;
        apiKey = (String) customCache.get(SysCfgEnum.SILICONFLOW_AI_API.getCode());
        if (StringUtils.isBlank(apiKey)) {
            OciKv cfg = kvService.getOne(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, SysCfgEnum.SILICONFLOW_AI_API.getCode()));
            if (null == cfg || StringUtils.isBlank(cfg.getValue())) {
                return Flux.just("抱歉，您未配置API秘钥，服务暂时不可用。");
            } else {
                apiKey = cfg.getValue();
                customCache.put(SysCfgEnum.SILICONFLOW_AI_API.getCode(), apiKey, 24 * 60 * 60 * 1000);
            }
        }
        ChatClient chatClient = factory.create(apiKey, baseUrl, StringUtils.isBlank(model) ? defaultModel : model);

        // 拿当前 session 的历史消息（过期时间：30分钟）
        List<Message> history = (List<Message>) customCache.get(sessionId);
        if (history == null) {
            history = new ArrayList<>();
        }

        // 添加用户消息
        history.add(new UserMessage(message));

        try {
            List<Message> finalHistory = history;
            if (enableInternet != null && enableInternet) {
                return searchService.searchWithHtml(message)
                        .flatMapMany(results -> {
                            String prompt = message + "\n根据以下内容回答：\n" +
                                    String.join("\n", results);

                            return chatClient
                                    .prompt(prompt)
                                    .messages(finalHistory)
                                    .options(OpenAiChatOptions.builder()
                                            .model(StringUtils.isBlank(model) ? defaultModel : model)
                                            .build())
                                    .user(message)
                                    .stream()
                                    .content()
                                    .map(chunk -> chunk == null ? "" : chunk)
                                    .doOnNext(chunk -> {
                                        // 把 AI 回复追加进历史
                                        finalHistory.add(new AssistantMessage(chunk));
                                        customCache.put(sessionId, finalHistory, 30 * 60 * 1000); // 30分钟对话过期
                                    })
                                    .bufferUntil(chunk -> chunk.endsWith("。") || chunk.endsWith("\n") || chunk.endsWith("."))
                                    .map(list -> String.join("", list))
                                    .onErrorResume(error -> {
                                        // 错误处理
                                        log.error("Stream error: ", error);
                                        return Flux.just("抱歉，处理您的请求时出现了错误。");
                                    });
                        });
            } else {
                return chatClient.prompt()
                        .messages(finalHistory)
                        .options(OpenAiChatOptions.builder()
                                .model(StringUtils.isBlank(model) ? defaultModel : model)
                                .build())
                        .stream()
                        .content()
                        .map(chunk -> chunk == null ? "" : chunk)
                        .doOnNext(chunk -> {
                            // 把 AI 回复追加进历史
                            finalHistory.add(new AssistantMessage(chunk));
                            customCache.put(sessionId, finalHistory, 30 * 60 * 1000); // 30分钟对话过期
                        })
                        .bufferUntil(chunk -> chunk.endsWith("。") || chunk.endsWith("\n") || chunk.endsWith("."))
                        .map(list -> String.join("", list))
                        .onErrorResume(error -> {
                            log.error("Stream error: ", error);
                            return Flux.just("抱歉，处理您的请求时出现了错误。");
                        });
            }
        } catch (Exception e) {
            log.error("Chat error: ", e);
            return Flux.just("抱歉，服务暂时不可用。");
        }
    }
}
