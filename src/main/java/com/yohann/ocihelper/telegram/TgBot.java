package com.yohann.ocihelper.telegram;

import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.factory.CallbackHandlerFactory;
import com.yohann.ocihelper.telegram.handler.CallbackHandler;
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
     * 处理文本消息（命令）
     */
    private void handleTextMessage(Update update) {
        String messageText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();

        // 检查权限
        if (!isAuthorized(chatId)) {
            sendUnauthorizedMessage(chatId);
            return;
        }

        // 处理 /start 命令
        if ("/start".equals(messageText)) {
            sendMainMenu(chatId);
        }
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
}
