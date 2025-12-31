package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import com.yohann.ocihelper.telegram.service.TelegramBotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.util.Arrays;

/**
 * API test alive callback handler
 * 
 * @author yohann
 */
@Slf4j
@Component
public class CheckAliveHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        // Show confirmation dialog
        return buildEditMessage(
                callbackQuery,
                "⚠️ 确认执行一键测活操作？\n\n此操作将测试所有OCI配置的有效性。",
                InlineKeyboardMarkup.builder()
                        .keyboard(Arrays.asList(
                                new InlineKeyboardRow(
                                        InlineKeyboardButton.builder()
                                                .text("✅ 确认执行")
                                                .callbackData("check_alive_confirm")
                                                .build(),
                                        InlineKeyboardButton.builder()
                                                .text("❌ 取消")
                                                .callbackData("back_to_main")
                                                .build()
                                )
                        ))
                        .build()
        );
    }
    
    @Override
    public String getCallbackPattern() {
        return "check_alive";
    }
}

/**
 * Check alive confirmation handler
 * 
 * @author yohann
 */
@Slf4j
@Component
class CheckAliveConfirmHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        TelegramBotService botService = SpringUtil.getBean(TelegramBotService.class);
        String result = botService.checkAlive();
        
        return buildEditMessage(
                callbackQuery,
                result,
                new InlineKeyboardMarkup(KeyboardBuilder.buildMainMenu())
        );
    }
    
    @Override
    public String getCallbackPattern() {
        return "check_alive_confirm";
    }
}
