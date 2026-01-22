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
 * IP Blacklist Clear Handler
 * Clears all blacklist entries
 */
@Slf4j
@Component
public class IpBlacklistClearHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        IpSecurityService ipSecurityService = SpringUtil.getBean(IpSecurityService.class);
        
        try {
            ipSecurityService.clearBlacklist();
            
            String text = "✅ *黑名单已清空*\n\n" +
                         "所有黑名单条目已被清除。\n\n" +
                         "💡 提示：\n" +
                         "• 所有IP现在都可以访问系统\n" +
                         "• 如需重新禁止，请添加IP到黑名单";
            
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
            
        } catch (Exception e) {
            log.error("Failed to clear blacklist", e);
            
            String text = "❌ *清空黑名单失败*\n\n" +
                         "错误信息：" + e.getMessage();
            
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
    }
    
    @Override
    public String getCallbackPattern() {
        return "ip_blacklist_clear";
    }
}
