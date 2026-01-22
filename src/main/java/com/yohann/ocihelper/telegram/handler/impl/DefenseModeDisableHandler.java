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
 * Defense Mode Disable Handler
 * Disables defense mode
 */
@Slf4j
@Component
public class DefenseModeDisableHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        IpSecurityService ipSecurityService = SpringUtil.getBean(IpSecurityService.class);
        
        try {
            ipSecurityService.toggleDefenseMode();
            
            String text = "🟢 *防御模式已关闭*\n\n" +
                         "✅ 操作成功！\n\n" +
                         "📌 *当前状态：*\n" +
                         "• 系统已恢复正常访问\n" +
                         "• Web界面可以访问\n" +
                         "• API接口可以调用\n" +
                         "• IP黑名单仍然生效\n\n" +
                         "💡 *提示：*\n" +
                         "• 请继续关注系统安全\n" +
                         "• 如有异常可随时启用防御模式\n" +
                         "• 建议配合IP黑名单使用";
            
            List<InlineKeyboardRow> keyboard = new ArrayList<>();
            keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回防御模式", "defense_mode")
            ));
            keyboard.add(KeyboardBuilder.buildBackToMainMenuRow());
            keyboard.add(KeyboardBuilder.buildCancelRow());
            
            log.info("Defense mode disabled via Telegram Bot");
            
            return buildEditMessage(
                callbackQuery,
                text,
                new InlineKeyboardMarkup(keyboard)
            );
            
        } catch (Exception e) {
            log.error("Failed to disable defense mode", e);
            
            String text = "❌ *关闭防御模式失败*\n\n" +
                         "错误信息：" + e.getMessage();
            
            List<InlineKeyboardRow> keyboard = new ArrayList<>();
            keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回", "defense_mode")
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
        return "defense_mode_disable";
    }
}
