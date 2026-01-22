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
 * IP Blacklist Add Handler
 * Prompts user to input IP to add
 */
@Slf4j
@Component
public class IpBlacklistAddHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        
        // Set session state to wait for IP input
        ConfigSessionStorage configStorage = ConfigSessionStorage.getInstance();
        configStorage.startSession(chatId, ConfigSessionStorage.SessionType.IP_BLACKLIST_ADD);
        
        String text = "➕ *添加IP到黑名单*\n\n" +
                     "请发送要添加的IP地址\n\n" +
                     "📝 *格式示例：*\n" +
                     "• 192.168.1.100\n" +
                     "• 10.0.0.50\n" +
                     "• 172.16.0.1\n\n" +
                     "⚠️ *注意：*\n" +
                     "• 请确保IP格式正确\n" +
                     "• 添加后该IP将无法访问系统\n" +
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
        return "ip_blacklist_add";
    }
}
