package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.service.IpSecurityService;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
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
 * IP Blacklist Clear Confirm Handler
 * Asks for confirmation before clearing blacklist
 */
@Slf4j
@Component
public class IpBlacklistClearConfirmHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String text = "⚠️ *确认清空黑名单*\n\n" +
                     "您确定要清空所有黑名单条目吗？\n\n" +
                     "清空后：\n" +
                     "• 所有被禁止的IP都将可以访问\n" +
                     "• 此操作不可恢复\n" +
                     "• 需要重新添加才能再次禁止\n\n" +
                     "💡 提示：\n" +
                     "如果只是要删除某个IP，请使用「删除IP」功能。";
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("✅ 确认清空", "ip_blacklist_clear"),
            KeyboardBuilder.button("❌ 取消", "ip_blacklist")
        ));
        
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
        return "ip_blacklist_clear_confirm";
    }
}
