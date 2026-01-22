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
 * IP Blacklist Add Range Handler
 * Prompts user to input IP range (CIDR) to add
 */
@Slf4j
@Component
public class IpBlacklistAddRangeHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        
        // Set session state to wait for IP range input
        ConfigSessionStorage configStorage = ConfigSessionStorage.getInstance();
        configStorage.startSession(chatId, ConfigSessionStorage.SessionType.IP_BLACKLIST_ADD_RANGE);
        
        String text = "➕ *添加IP段到黑名单*\n\n" +
                     "请发送要添加的IP段（CIDR格式）\n\n" +
                     "📝 *格式示例：*\n" +
                     "• 192.168.1.0/24 （256个IP）\n" +
                     "• 10.0.0.0/16 （6.5万个IP）\n" +
                     "• 172.16.0.0/12\n\n" +
                     "⚠️ *注意：*\n" +
                     "• 请确保CIDR格式正确\n" +
                     "• 添加后整个IP段无法访问\n" +
                     "• 请谨慎操作避免误伤\n" +
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
        return "ip_blacklist_add_range";
    }
}
