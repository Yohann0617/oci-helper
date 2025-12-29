package com.yohann.ocihelper.telegram.handler;

import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

import static java.lang.Math.toIntExact;

/**
 * 抽象基础回调处理器
 * 
 * @author yohann
 */
public abstract class AbstractCallbackHandler implements CallbackHandler {
    
    /**
     * 构建编辑消息文本
     * 
     * @param callbackQuery 回调查询
     * @param text 消息文本
     * @param markup 内联键盘标记
     * @return 编辑消息文本
     */
    protected EditMessageText buildEditMessage(CallbackQuery callbackQuery, String text, InlineKeyboardMarkup markup) {
        return EditMessageText.builder()
                .chatId(callbackQuery.getMessage().getChatId())
                .messageId(toIntExact(callbackQuery.getMessage().getMessageId()))
                .text(text)
                .replyMarkup(markup)
                .build();
    }
    
    /**
     * 构建编辑消息文本（不带标记）
     * 
     * @param callbackQuery 回调查询
     * @param text 消息文本
     * @return 编辑消息文本
     */
    protected EditMessageText buildEditMessage(CallbackQuery callbackQuery, String text) {
        return buildEditMessage(callbackQuery, text, null);
    }
}
