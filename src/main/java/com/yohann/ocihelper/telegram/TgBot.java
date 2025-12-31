package com.yohann.ocihelper.telegram;

import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.factory.CallbackHandlerFactory;
import com.yohann.ocihelper.telegram.handler.CallbackHandler;
import com.yohann.ocihelper.telegram.service.AiChatService;
import com.yohann.ocihelper.telegram.service.SshService;
import com.yohann.ocihelper.telegram.storage.SshConnectionStorage;
import com.yohann.ocihelper.telegram.utils.MarkdownFormatter;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Telegram Bot 主类
 * 使用命令模式重构的模块化架构
 *
 * @author Yohann_Fan
 */
@Slf4j
public class TgBot implements LongPollingSingleThreadUpdateConsumer {

    private final String BOT_TOKEN;
    private final String CHAT_ID;
    private final TelegramClient telegramClient;

    public TgBot(String botToken, String chatId) {
        BOT_TOKEN = botToken;
        CHAT_ID = chatId;
        telegramClient = new OkHttpTelegramClient(BOT_TOKEN);
    }

    @Override
    public void consume(List<Update> updates) {
        LongPollingSingleThreadUpdateConsumer.super.consume(updates);
    }

    @Override
    public void consume(Update update) {
        // 处理文本消息
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update);
            return;
        }

        // 处理回调查询
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    /**
     * 处理文本消息（命令和对话）
     */
    private void handleTextMessage(Update update) {
        String messageText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();

        // 检查权限
        if (!isAuthorized(chatId)) {
            sendUnauthorizedMessage(chatId);
            return;
        }

        // 处理命令
        if (messageText.startsWith("/")) {
            handleCommand(chatId, messageText);
        } else {
            // 非命令消息，当作 AI 对话处理
            handleAiChat(chatId, messageText);
        }
    }

    /**
     * 处理命令
     */
    private void handleCommand(long chatId, String command) {
        if ("/start".equals(command)) {
            sendMainMenu(chatId);
        } else if (command.startsWith("/ssh_config ")) {
            handleSshConfig(chatId, command);
        } else if (command.startsWith("/ssh ")) {
            handleSshCommand(chatId, command);
        } else if ("/help".equals(command)) {
            sendHelpMessage(chatId);
        } else {
            sendMessage(chatId, "❌ 未知命令，输入 /help 查看帮助");
        }
    }

    /**
     * 处理 SSH 配置命令
     */
    private void handleSshConfig(long chatId, String command) {
        try {
            // Format: /ssh_config host port username password
            String[] parts = command.substring(12).trim().split("\\s+");

            if (parts.length < 3) {
                sendMessage(chatId,
                        "❌ 参数不足\n\n" +
                                "格式: /ssh_config host port username password\n" +
                                "例如: /ssh_config 192.168.1.100 22 root mypassword"
                );
                return;
            }

            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 22;
            String username = parts.length > 2 ? parts[2] : "root";
            String password = parts.length > 3 ? parts[3] : "";

            // Test connection first
            SshService sshService = SpringUtil.getBean(SshService.class);
            sendMessage(chatId, "🔄 正在测试连接...");

            if (sshService.testConnection(host, port, username, password)) {
                SshConnectionStorage.getInstance().saveConnection(chatId, host, port, username, password);
                sendMessage(chatId,
                        String.format(
                                "✅ SSH 连接配置成功\n\n" +
                                        "主机: %s:%d\n" +
                                        "用户: %s\n\n" +
                                        "现在可以使用 /ssh [命令] 来执行命令了",
                                host, port, username
                        )
                );
                log.info("SSH connection configured: chatId={}, host={}", chatId, host);
            } else {
                sendMessage(chatId, "❌ 连接测试失败，请检查配置是否正确");
            }

        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ 端口号格式错误");
        } catch (Exception e) {
            log.error("Failed to configure SSH", e);
            sendMessage(chatId, "❌ 配置失败: " + e.getMessage());
        }
    }

    /**
     * 处理 SSH 命令执行（异步执行避免阻塞）
     */
    private void handleSshCommand(long chatId, String command) {
        SshConnectionStorage storage = SshConnectionStorage.getInstance();

        if (!storage.hasConnection(chatId)) {
            sendMessage(chatId,
                    "❌ 未配置 SSH 连接\n\n" +
                            "请使用 /ssh_config 命令配置连接信息"
            );
            return;
        }

        try {
            // Get command (remove /ssh prefix)
            String sshCommand = command.substring(5).trim();

            if (sshCommand.isEmpty()) {
                sendMessage(chatId, "❌ 请输入要执行的命令\n\n例如: /ssh ls -la");
                return;
            }

            // Send executing message
            sendMessage(chatId, "⏳ 正在执行命令...");

            // Execute command asynchronously to avoid blocking
            SshConnectionStorage.SshInfo info = storage.getConnection(chatId);
            SshService sshService = SpringUtil.getBean(SshService.class);

            CompletableFuture.supplyAsync(() -> {
                return sshService.executeCommand(
                        info.getHost(),
                        info.getPort(),
                        info.getUsername(),
                        info.getPassword(),
                        sshCommand
                );
            }).thenAccept(result -> {
                // Format and send result (with Markdown enabled for code blocks)
                String formattedResult = sshService.formatOutput(result);
                sendMessage(chatId, formattedResult, true);
                log.info("SSH command executed: chatId={}, command={}", chatId, sshCommand);
            }).exceptionally(ex -> {
                log.error("Failed to execute SSH command", ex);
                sendMessage(chatId, "❌ 执行失败: " + ex.getMessage());
                return null;
            });

        } catch (Exception e) {
            log.error("Failed to handle SSH command", e);
            sendMessage(chatId, "❌ 处理失败: " + e.getMessage());
        }
    }

    /**
     * 处理 AI 对话
     */
    private void handleAiChat(long chatId, String message) {
        try {
            // Send typing indicator
            sendMessage(chatId, "🤔 思考中...", false);

            // Call AI service asynchronously
            AiChatService aiChatService = SpringUtil.getBean(AiChatService.class);
            CompletableFuture<String> future = aiChatService.chat(chatId, message);

            // Wait for response and send
            future.thenAccept(response -> {
                // Format response with proper Markdown
                String formattedResponse = MarkdownFormatter.formatAiResponse(response);
                sendMessage(chatId, formattedResponse, true);
            }).exceptionally(ex -> {
                log.error("AI chat failed", ex);
                sendMessage(chatId, "❌ AI 对话失败: " + ex.getMessage(), false);
                return null;
            });

        } catch (Exception e) {
            log.error("Failed to handle AI chat", e);
            sendMessage(chatId, "❌ 处理失败: " + e.getMessage(), false);
        }
    }

    /**
     * 发送帮助消息
     */
    private void sendHelpMessage(long chatId) {
        String helpText =
                "📖 *命令帮助*\n\n" +
                        "*基础命令：*\n" +
                        "├ `/start` - 显示主菜单\n" +
                        "├ `/help` - 显示此帮助信息\n\n" +
                        "*AI 聊天：*\n" +
                        "├ 直接发送消息即可与 AI 对话\n" +
                        "├ 在主菜单选择 \"AI 聊天\" 进行设置\n\n" +
                        "*SSH 管理：*\n" +
                        "├ `/ssh_config host port user pwd` - 配置连接\n" +
                        "├ `/ssh [命令]` - 执行 SSH 命令\n" +
                        "└ 示例: `/ssh ls -la`\n\n" +
                        "💡 更多功能请点击 /start 查看主菜单";

        // Format and send with Markdown enabled
        String formattedText = MarkdownFormatter.formatMarkdown(helpText);
        sendMessage(chatId, formattedText, true);
    }

    /**
     * 使用处理器工厂处理回调查询
     */
    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();

        // 检查权限
        if (!isAuthorized(chatId)) {
            sendUnauthorizedMessage(chatId);
            return;
        }

        try {
            CallbackHandlerFactory factory = SpringUtil.getBean(CallbackHandlerFactory.class);
            CallbackHandler handler = factory.getHandler(callbackData).orElse(null);

            if (handler != null) {
                BotApiMethod<? extends Serializable> response = handler.handle(
                        update.getCallbackQuery(),
                        telegramClient
                );

                if (response != null) {
                    telegramClient.execute(response);
                }
            } else {
                log.warn("未找到处理回调数据的处理器: {}", callbackData);
            }
        } catch (TelegramApiException e) {
            log.error("处理回调查询失败: callbackData={}", callbackData, e);
        } catch (Exception e) {
            log.error("处理回调时发生意外错误: callbackData={}", callbackData, e);
        }
    }

    /**
     * 检查用户是否有权限
     */
    private boolean isAuthorized(long chatId) {
        return CHAT_ID.equals(String.valueOf(chatId));
    }

    /**
     * 发送无权限消息
     */
    private void sendUnauthorizedMessage(long chatId) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("❌ 无权限操作此机器人🤖，项目地址：https://github.com/Yohann0617/oci-helper")
                    .build());
        } catch (TelegramApiException e) {
            log.error("发送无权限消息失败", e);
        }
    }

    /**
     * 发送主菜单
     */
    private void sendMainMenu(long chatId) {
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("请选择需要执行的操作：")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboard(KeyboardBuilder.buildMainMenu())
                            .build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("发送主菜单失败", e);
        }
    }

    /**
     * 发送普通消息
     *
     * @param chatId         chat ID
     * @param text           message text
     * @param enableMarkdown whether to enable Markdown parsing
     */
    private void sendMessage(long chatId, String text, boolean enableMarkdown) {
        try {
            // Truncate message if too long
            String truncatedText = MarkdownFormatter.truncate(text);

            SendMessage.SendMessageBuilder builder = SendMessage.builder()
                    .chatId(chatId)
                    .text(truncatedText);

            // Enable Markdown only if requested
            if (enableMarkdown) {
                builder.parseMode("Markdown");
            }

            telegramClient.execute(builder.build());
        } catch (TelegramApiException e) {
            log.error("发送消息失败: text={}", text, e);

            // Fallback: try sending without Markdown
            if (enableMarkdown) {
                try {
                    telegramClient.execute(SendMessage.builder()
                            .chatId(chatId)
                            .text(text)
                            .build());
                    log.info("消息重新发送成功（不使用 Markdown）");
                } catch (TelegramApiException fallbackEx) {
                    log.error("消息重新发送也失败", fallbackEx);
                }
            }
        }
    }

    /**
     * 发送普通消息（默认不启用 Markdown）
     */
    private void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, false);
    }
}
