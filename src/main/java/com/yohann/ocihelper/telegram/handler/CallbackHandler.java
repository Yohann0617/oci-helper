package com.yohann.ocihelper.telegram.handler;

import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;

/**
 * Telegram Bot 回调处理器接口
 * 
 * @author yohann
 */
public interface CallbackHandler {
    
    /**
     * 处理回调查询
     * 
     * @param callbackQuery 来自 Telegram 的回调查询
     * @param telegramClient Telegram 客户端
     * @return 要执行的 Bot API 方法（如果内部处理则可以为 null）
     */
    BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient);
    
    /**
     * 获取此处理器支持的回调数据模式
     * 
     * @return 回调数据模式
     */
    String getCallbackPattern();
    
    /**
     * 检查此处理器是否可以处理回调
     * 
     * @param callbackData 回调数据
     * @return 如果可以处理则返回 true
     */
    default boolean canHandle(String callbackData) {
        return callbackData != null && callbackData.startsWith(getCallbackPattern());
    }
}
