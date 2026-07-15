package com.yohann.ocihelper.telegram.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yohann.ocihelper.bean.entity.OciKv;
import com.yohann.ocihelper.config.ai.DynamicChatClientFactory;
import com.yohann.ocihelper.enums.SysCfgEnum;
import com.yohann.ocihelper.service.IOciKvService;
import com.yohann.ocihelper.telegram.storage.ChatSessionStorage;
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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import reactor.core.publisher.Flux;

/**
 * AI Chat Service for Telegram Bot
 * Provides streaming chat functionality
 *
 * @author yohann
 */
@Slf4j
@Service
public class AiChatService {

    @Resource
    private CustomExpiryGuavaCache<String, Object> customCache;

    @Resource
    private IOciKvService kvService;

    private final DynamicChatClientFactory factory;
    private final DuckDuckGoSearchService searchService;

    public AiChatService(DynamicChatClientFactory factory,
                         DuckDuckGoSearchService searchService) {
        this.factory = factory;
        this.searchService = searchService;
    }

    // Cache for ChatClient instances
    private ChatClient cachedChatClient = null;
    private String cachedModel = null;

    /**
     * Send message to AI and get streaming response
     * Returns a Flux that emits chunks of the AI response as they arrive
     *
     * @param chatId  Telegram chat ID
     * @param message user message
     * @return Flux of response chunks
     */
    public Flux<String> chatStream(long chatId, String message) {
        return Flux.defer(() -> {
            try {
                ChatSessionStorage storage = ChatSessionStorage.getInstance();

                // 获取设置
                String model = storage.getModel(chatId);
                boolean enableInternet = storage.isInternetEnabled(chatId);
                String sessionId = storage.getOrCreateSessionId(chatId);

                // 获取 API 密钥
                String apiKey = getApiKey();
                if (StringUtils.isBlank(apiKey)) {
                    return Flux.just("❌ 未配置 AI API 密钥，请在系统配置中设置");
                }

                // 获取或创建 ChatClient
                ChatClient chatClient = getOrCreateChatClient(apiKey, model);

                // 构建消息历史
                List<Message> history = buildMessageHistory(chatId, message);

                // 用于收集完整响应的容器
                AtomicReference<String> fullResponse = new AtomicReference<>("");

                // 调用 AI 流式接口
                Flux<String> streamFlux;
                if (enableInternet) {
                    streamFlux = chatWithInternetStream(chatClient, message, model, history);
                } else {
                    streamFlux = chatNormalStream(chatClient, model, history);
                }

                return streamFlux
                        .doOnNext(chunk -> {
                            // 累积完整响应
                            fullResponse.updateAndGet(current -> current + chunk);
                        })
                        .doOnComplete(() -> {
                            // 流式完成后，存储完整消息历史
                            // 【关键】双轨存储：给 AI 看的原始文本（不含格式标记）和给用户看的格式化文本
                            String completeResponse = fullResponse.get();
                            // 提取纯文本（去掉 thinking 标签），用于 AI 上下文
                            String rawResponse = extractRawText(completeResponse);
                            // 格式化后的文本，用于 Telegram 展示
                            String formattedResponse = formatAiResponse(completeResponse);
                            // 存储用户消息（原始）
                            storage.addMessage(chatId, "User: " + message);
                            // 存储 AI 回复（原始文本，不含格式标记）
                            storage.addMessage(chatId, "AI: " + rawResponse);
                        })
                        .doOnError(e -> {
                            log.error("AI chat stream failed: chatId={}, message={}", chatId, message, e);
                        })
                        .onErrorResume(e -> Flux.just("\n\n❌ AI 对话失败: " + e.getMessage()));

            } catch (Exception e) {
                log.error("AI chat stream init failed: chatId={}, message={}", chatId, message, e);
                return Flux.just("❌ AI 对话失败: " + e.getMessage());
            }
        });
    }

    /**
     * 提取纯文本内容，去掉  thinking 标签，只保留回答部分
     * 用于存储到历史记录中作为 AI 上下文，避免格式标记干扰后续对话
     */
    private String extractRawText(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        int thinkEnd = response.indexOf(" response");
        if (thinkEnd != -1) {
            return response.substring(thinkEnd + 8).trim();
        }
        return response;
    }

    /**
     * Format AI response to separate thinking and answer
     * Extracts  thinking tags and formats them as code blocks
     * Properly handles Markdown formatting in answer
     *
     * @param response raw AI response
     * @return formatted response
     */
    private String formatAiResponse(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }

        // Check if response contains thinking tags
        if (response.contains("<think>") || response.contains("</think>")) {
            StringBuilder formatted = new StringBuilder();

            // Extract thinking part (between <think> and </think>)
            int thinkStart = response.indexOf("<think>");
            int thinkEnd = response.indexOf("</think>");

            if (thinkStart != -1 && thinkEnd != -1 && thinkEnd > thinkStart) {
                // Extract thinking content
                String thinking = response.substring(thinkStart + 7, thinkEnd).trim();

                // Extract answer part (after </think>)
                String answer = response.substring(thinkEnd + 8).trim();

                // Format with thinking as code block
                if (!thinking.isEmpty()) {
                    formatted.append("💭 *AI 思考过程：*\n");
                    formatted.append("```\n");
                    formatted.append(thinking);
                    formatted.append("\n```\n\n");
                }

                // Add answer with Markdown support
                if (!answer.isEmpty()) {
                    formatted.append("💬 *回答：*\n");
                    // Process answer to support Markdown properly
                    formatted.append(processMarkdownContent(answer));
                } else {
                    formatted.append(processMarkdownContent(answer));
                }

                return formatted.toString();
            } else if (thinkEnd != -1) {
                // Only </think> found, everything after is answer
                String answer = response.substring(thinkEnd + 8).trim();
                return "💬 *回答：*\n" + processMarkdownContent(answer);
            }
        }

        // No thinking tags, process the whole response for Markdown
        return processMarkdownContent(response);
    }

    /**
     * Process content to preserve Markdown formatting
     * Protects code blocks and inline code from being escaped
     * Allows bold, italic, links, headers, and lists to work properly
     * <p>
     * Supported Markdown syntax:
     * - ```code blocks```
     * - `inline code`
     * - **bold**, *italic*, _italic_
     * - [links](url)
     * - # Headers (at line start)
     * - - Lists (at line start)
     *
     * @param content raw content
     * @return processed content with Markdown support
     */
    private String processMarkdownContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        // Strategy: Preserve existing Markdown syntax
        // - Keep ```code blocks``` intact
        // - Keep `inline code` intact
        // - Keep **bold**, *italic*, _italic_ intact
        // - Keep [links](url) intact
        // - Keep # headers (at line start) intact
        // - Keep - lists (at line start) intact
        // - Escape problematic standalone characters

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = content.length();

        while (i < len) {
            char c = content.charAt(i);

            // Handle code blocks ```
            if (i + 2 < len && content.substring(i, i + 3).equals("```")) {
                // Find the closing ```
                int closeIndex = content.indexOf("```", i + 3);
                if (closeIndex != -1) {
                    // Include entire code block
                    result.append(content, i, closeIndex + 3);
                    i = closeIndex + 3;
                    continue;
                } else {
                    // No closing found, just append ```
                    result.append("```");
                    i += 3;
                    continue;
                }
            }

            // Handle inline code `
            if (c == '`') {
                // Find the closing `
                int closeIndex = content.indexOf('`', i + 1);
                if (closeIndex != -1) {
                    // Include entire inline code
                    result.append(content, i, closeIndex + 1);
                    i = closeIndex + 1;
                    continue;
                } else {
                    // No closing found, escape the backtick
                    result.append("\\`");
                    i++;
                    continue;
                }
            }

            // Handle bold ** or *
            if (c == '*') {
                // Check if it's ** (bold) or * (italic)
                if (i + 1 < len && content.charAt(i + 1) == '*') {
                    // Find closing **
                    int closeIndex = content.indexOf("**", i + 2);
                    if (closeIndex != -1 && closeIndex - i - 2 > 0) {
                        // Valid bold, keep it
                        result.append(content, i, closeIndex + 2);
                        i = closeIndex + 2;
                        continue;
                    }
                } else {
                    // Check for single * (italic)
                    int closeIndex = content.indexOf('*', i + 1);
                    if (closeIndex != -1 && closeIndex - i - 1 > 0) {
                        // Valid italic, keep it
                        result.append(content, i, closeIndex + 1);
                        i = closeIndex + 1;
                        continue;
                    }
                }
                // Standalone *, keep it (Telegram Markdown is lenient)
                result.append('*');
                i++;
                continue;
            }

            // Handle italic _
            if (c == '_') {
                // Find closing _
                int closeIndex = content.indexOf('_', i + 1);
                if (closeIndex != -1 && closeIndex - i - 1 > 0) {
                    // Valid italic, keep it
                    result.append(content, i, closeIndex + 1);
                    i = closeIndex + 1;
                    continue;
                }
                // Standalone _, escape it
                result.append("\\_");
                i++;
                continue;
            }

            // Handle links [text](url)
            if (c == '[') {
                // Find ]
                int closeBracket = content.indexOf(']', i + 1);
                if (closeBracket != -1 && closeBracket + 1 < len && content.charAt(closeBracket + 1) == '(') {
                    // Find closing )
                    int closeParen = content.indexOf(')', closeBracket + 2);
                    if (closeParen != -1) {
                        // Valid link, keep it
                        result.append(content, i, closeParen + 1);
                        i = closeParen + 1;
                        continue;
                    }
                }
                // Not a valid link, escape [
                result.append("\\[");
                i++;
                continue;
            }

            // Handle headers # (at start of line)
            if (c == '#' && (i == 0 || content.charAt(i - 1) == '\n')) {
                // Count consecutive #
                int hashCount = 0;
                int j = i;
                while (j < len && content.charAt(j) == '#') {
                    hashCount++;
                    j++;
                }
                // If followed by space, it's a header - keep as-is
                if (j < len && content.charAt(j) == ' ') {
                    result.append(content, i, j);
                    i = j;
                    continue;
                }
                // Otherwise, escape single #
                result.append("\\#");
                i++;
                continue;
            }

            // Handle lists - (at start of line)
            if (c == '-' && (i == 0 || content.charAt(i - 1) == '\n')) {
                // If followed by space, it's a list item - keep as-is
                if (i + 1 < len && content.charAt(i + 1) == ' ') {
                    result.append('-');
                    i++;
                    continue;
                }
            }

            // For other - characters, check context
            if (c == '-') {
                // Keep - as-is (Telegram Markdown is lenient with -)
                result.append('-');
                i++;
                continue;
            }

            // Handle # outside of line start (keep as-is for Telegram)
            if (c == '#') {
                result.append('#');
                i++;
                continue;
            }

            // Regular character, keep as-is
            result.append(c);
            i++;
        }

        return result.toString();
    }

    /**
     * Get or create ChatClient
     */
    private synchronized ChatClient getOrCreateChatClient(String apiKey, String model) {
        if (cachedChatClient == null || !model.equals(cachedModel)) {
            String baseUrl = "https://api.siliconflow.cn";
            cachedChatClient = factory.create(apiKey, baseUrl, model);
            cachedModel = model;
            log.info("Created new ChatClient: model={}", model);
        }
        return cachedChatClient;
    }

    /**
     * Build message history
     */
    private List<Message> buildMessageHistory(long chatId, String currentMessage) {
        ChatSessionStorage storage = ChatSessionStorage.getInstance();
        List<String> historyStrings = storage.getHistory(chatId);

        List<Message> messages = new ArrayList<>();

        // Add history messages
        for (String msg : historyStrings) {
            if (msg.startsWith("User: ")) {
                messages.add(new UserMessage(msg.substring(6)));
            } else if (msg.startsWith("AI: ")) {
                messages.add(new AssistantMessage(msg.substring(4)));
            }
        }

        // Add current message
        messages.add(new UserMessage(currentMessage));

        return messages;
    }

    /**
     * 流式对话（带联网搜索）
     */
    private Flux<String> chatWithInternetStream(ChatClient chatClient, String message,
                                                String model, List<Message> history) {
        return searchService.searchWithHtml(message)
                .flatMapMany(results -> {
                    if (results != null && !results.isEmpty()) {
                        String prompt = message + "\n\n根据以下搜索结果回答：\n" +
                                String.join("\n", results);

                        return chatClient.prompt(prompt)
                                .messages(history)
                                .options(OpenAiChatOptions.builder()
                                        .model(model)
                                        .build())
                                .stream()
                                .content()
                                .map(chunk -> chunk == null ? "" : chunk);
                    } else {
                        // 搜索无结果，回退到普通流式对话
                        return chatNormalStream(chatClient, model, history);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Internet search failed, fallback to normal stream chat", e);
                    return chatNormalStream(chatClient, model, history);
                });
    }

    /**
     * 普通流式对话（不带联网搜索）
     */
    private Flux<String> chatNormalStream(ChatClient chatClient, String model,
                                          List<Message> history) {
        return chatClient.prompt()
                .messages(history)
                .options(OpenAiChatOptions.builder()
                        .model(model)
                        .build())
                .stream()
                .content()
                .map(chunk -> chunk == null ? "" : chunk);
    }

    /**
     * Get API key from database
     */
    private String getApiKey() {
        String apiKey = (String) customCache.get(SysCfgEnum.SILICONFLOW_AI_API.getCode());
        if (StringUtils.isBlank(apiKey)) {
            OciKv cfg = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                    .eq(OciKv::getCode, SysCfgEnum.SILICONFLOW_AI_API.getCode()));
            if (cfg != null && StringUtils.isNotBlank(cfg.getValue())) {
                apiKey = cfg.getValue();
                customCache.put(SysCfgEnum.SILICONFLOW_AI_API.getCode(), apiKey,
                        24 * 60 * 60 * 1000);
            }
        }
        return apiKey;
    }
}
