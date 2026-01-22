package com.yohann.ocihelper.telegram.handler.impl;

import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import com.yohann.ocihelper.telegram.storage.ConfigSessionStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * IP Blacklist Remove Handler
 * Prompts user to input IP to remove
 */
@Slf4j
@Component
public class IpBlacklistRemoveHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        
        // Set session state to wait for IP input
        ConfigSessionStorage configStorage = ConfigSessionStorage.getInstance();
        configStorage.startSession(chatId, ConfigSessionStorage.SessionType.IP_BLACKLIST_REMOVE);
        
        String text = "➖ *从黑名单删除IP*\n\n" +
                     "请发送要删除的IP地址或IP段\n\n" +
                     "📝 *格式示例：*\n" +
                     "• 192.168.1.100\n" +
                     "• 192.168.1.0/24\n\n" +
                     "⚠️ *注意：*\n" +
                     "• 请输入完整的IP或IP段\n" +
                     "• 必须与添加时的格式完全一致\n" +
                     "• 发送 /cancel 取消操作";
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("◀️ 返回", "ip_blacklist")
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());
        
        return buildEditMessage(
            callbackQuery,
            text,
            new InlineKeyboardMarkup(keyboard)
        );
    }
    
    @Override
    public String getCallbackPattern() {
        return "ip_blacklist_remove";
    }
}
