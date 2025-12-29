package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.service.IOciUserService;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import com.yohann.ocihelper.telegram.model.InstancePlan;
import com.yohann.ocihelper.telegram.service.InstanceCreationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;

/**
 * 创建实例回调处理器
 * 
 * @author yohann
 */
@Slf4j
@Component
public class CreateInstanceHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String callbackData = callbackQuery.getData();
        String[] parts = callbackData.split(":");
        String userId = parts[1];
        String planType = parts[2];
        
        IOciUserService userService = SpringUtil.getBean(IOciUserService.class);
        OciUser user = userService.getById(userId);
        
        if (user == null) {
            return buildEditMessage(
                    callbackQuery,
                    "❌ 配置不存在",
                    new InlineKeyboardMarkup(KeyboardBuilder.buildMainMenu())
            );
        }
        
        // 获取方案详情
        InstancePlan plan = getPlanByType(planType);
        
        // 启动异步创建
        InstanceCreationService creationService = SpringUtil.getBean(InstanceCreationService.class);
        
        try {
            // 先删除回调消息
            telegramClient.execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage.builder()
                    .chatId(callbackQuery.getMessage().getChatId())
                    .messageId(Math.toIntExact(callbackQuery.getMessage().getMessageId()))
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to delete message", e);
        }
        
        // 发送创建中的消息
        String creatingMessage = String.format(
                "⏳ 正在创建实例...\n\n" +
                "🔑 配置名：%s\n" +
                "🌏 区域：%s\n" +
                "💻 方案：%s\n" +
                "⚙️ 配置：%dC%dG%dG\n" +
                "🏗️ 架构：%s\n" +
                "💿 系统：%s\n\n" +
                "请稍候，任务已提交...",
                user.getUsername(),
                user.getOciRegion(),
                planType.equals("plan1") ? "方案1" : "方案2",
                plan.getOcpus(),
                plan.getMemory(),
                plan.getDisk(),
                plan.getArchitecture(),
                plan.getOperationSystem()
        );
        
        // 异步提交创建任务
        creationService.createInstanceAsync(
                userId,
                plan,
                callbackQuery.getMessage().getChatId(),
                telegramClient
        );
        
        return SendMessage.builder()
                .chatId(callbackQuery.getMessage().getChatId())
                .text(creatingMessage)
                .build();
    }
    
    private InstancePlan getPlanByType(String planType) {
        if ("plan1".equals(planType)) {
            // AMD 1C1G
            return InstancePlan.builder()
                    .ocpus(1)
                    .memory(1)
                    .disk(50)
                    .architecture("AMD")
                    .operationSystem("Ubuntu")
                    .interval(80)
                    .createNumbers(1)
                    .build();
        } else {
            // ARM 1C6G
            return InstancePlan.builder()
                    .ocpus(1)
                    .memory(6)
                    .disk(50)
                    .architecture("ARM")
                    .operationSystem("Ubuntu")
                    .interval(80)
                    .createNumbers(1)
                    .build();
        }
    }
    
    @Override
    public String getCallbackPattern() {
        return "create_instance:";
    }
}
