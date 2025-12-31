package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.service.IOciUserService;
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
 * Show create instance plans handler
 * 
 * @author yohann
 */
@Slf4j
@Component
public class ShowCreatePlansHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String callbackData = callbackQuery.getData();
        String userId = callbackData.split(":")[1];
        
        IOciUserService userService = SpringUtil.getBean(IOciUserService.class);
        OciUser user = userService.getById(userId);
        
        if (user == null) {
            return buildEditMessage(
                    callbackQuery,
                    "❌ 配置不存在",
                    new InlineKeyboardMarkup(KeyboardBuilder.buildMainMenu())
            );
        }
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        // Plan 1: AMD 1C1G
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button(
                        "💻 方案1: 1台1核1G50G(AMD/Ubuntu/80s)",
                        "create_instance:" + userId + ":plan1"
                )
        ));
        
        // Plan 2: ARM 1C6G
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button(
                        "🖥 方案2: 1台1核6G50G(ARM/Ubuntu/80s)",
                        "create_instance:" + userId + ":plan2"
                )
        ));
        
        // Back button
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回", "select_config:" + userId)
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());
        
        // Format tenant create time
        String tenantCreateTimeStr = user.getTenantCreateTime() != null 
                ? user.getTenantCreateTime().toString().replace("T", " ")
                : "未知";
        
        String message = String.format(
                "【选择开机方案】\n\n" +
                "🔑 配置名：%s\n" +
                "🌏 区域：%s\n" +
                "👤 租户名：%s\n" +
                "📅 租户创建时间：%s\n\n" +
                "请选择开机方案：",
                user.getUsername(),
                user.getOciRegion(),
                user.getTenantName() != null ? user.getTenantName() : "未知",
                tenantCreateTimeStr
        );
        
        return buildEditMessage(
                callbackQuery,
                message,
                new InlineKeyboardMarkup(keyboard)
        );
    }
    
    @Override
    public String getCallbackPattern() {
        return "show_create_plans:";
    }
}
