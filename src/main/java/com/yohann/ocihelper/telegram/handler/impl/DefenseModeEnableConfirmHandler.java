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
 * Defense Mode Enable Confirm Handler
 * Asks for confirmation before enabling defense mode
 */
@Slf4j
@Component
public class DefenseModeEnableConfirmHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String text = "⚠️ *确认启用防御模式*\n\n" +
                     "您确定要启用防御模式吗？\n\n" +
                     "启用后将会：\n" +
                     "🚫 *立即阻止所有IP访问*\n" +
                     "• Web界面无法访问\n" +
                     "• API接口无法调用\n" +
                     "• 所有外部访问被拒绝\n" +
                     "• 仅Telegram Bot可以管理\n\n" +
                     "✅ *适用场景：*\n" +
                     "• 正在遭受攻击\n" +
                     "• 发现安全漏洞\n" +
                     "• 紧急维护需要\n" +
                     "• 需要完全隔离系统\n\n" +
                     "💡 *重要提示：*\n" +
                     "• 确保Telegram Bot正常工作\n" +
                     "• 否则可能无法恢复访问\n" +
                     "• 可随时通过Bot关闭\n\n" +
                     "⚠️ 请谨慎操作！";
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("✅ 确认启用", "defense_mode_enable"),
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
        return "defense_mode_enable_confirm";
    }
}
