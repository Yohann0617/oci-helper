package com.yohann.ocihelper.telegram.handler.impl;

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
 * Defense Mode Disable Confirm Handler
 * Asks for confirmation before disabling defense mode
 */
@Slf4j
@Component
public class DefenseModeDisableConfirmHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String text = "✅ *确认关闭防御模式*\n\n" +
                     "您确定要关闭防御模式吗？\n\n" +
                     "关闭后：\n" +
                     "🟢 *系统将恢复正常访问*\n" +
                     "• Web界面可以访问\n" +
                     "• API接口可以调用\n" +
                     "• IP黑名单仍然生效\n" +
                     "• 未被禁止的IP可以访问\n\n" +
                     "💡 *提示：*\n" +
                     "• 确保系统已经安全\n" +
                     "• 确认威胁已经解除\n" +
                     "• 可以随时重新启用";
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("✅ 确认关闭", "defense_mode_disable"),
            KeyboardBuilder.button("❌ 取消", "defense_mode")
        ));
        
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
    
    @Override
    public String getCallbackPattern() {
        return "defense_mode_disable_confirm";
    }
}
