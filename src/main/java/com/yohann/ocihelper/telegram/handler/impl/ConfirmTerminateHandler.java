package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import com.yohann.ocihelper.telegram.storage.InstanceSelectionStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.yohann.ocihelper.config.VirtualThreadConfig.VIRTUAL_EXECUTOR;

/**
 * Confirm terminate instances handler
 * 
 * @author yohann
 */
@Slf4j
@Component
public class ConfirmTerminateHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        InstanceSelectionStorage storage = InstanceSelectionStorage.getInstance();
        Set<String> selectedInstances = storage.getSelectedInstances(chatId);
        
        if (selectedInstances.isEmpty()) {
            try {
                telegramClient.execute(AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackQuery.getId())
                        .text("请先选择要终止的实例")
                        .showAlert(true)
                        .build());
            } catch (TelegramApiException e) {
                log.error("Failed to answer callback query", e);
            }
            return null;
        }
        
        List<InlineKeyboardRow> keyboard = List.of(
                new InlineKeyboardRow(
                        KeyboardBuilder.button("✅ 保留引导卷", "terminate_instances:true")
                ),
                new InlineKeyboardRow(
                        KeyboardBuilder.button("❌ 不保留引导卷", "terminate_instances:false")
                ),
                new InlineKeyboardRow(
                        KeyboardBuilder.button("◀️ 返回", "instance_management:" + storage.getConfigContext(chatId))
                ),
                KeyboardBuilder.buildCancelRow()
        );
        
        String message = String.format(
                "【确认终止实例】\n\n" +
                "⚠️ 您选择了 %d 个实例，即将终止这些实例。\n\n" +
                "请选择是否保留引导卷：\n" +
                "• 保留：preserveBootVolume = true\n" +
                "• 不保留：preserveBootVolume = false\n\n" +
                "注意：此操作不可逆！",
                selectedInstances.size()
        );
        
        return buildEditMessage(
                callbackQuery,
                message,
                new InlineKeyboardMarkup(keyboard)
        );
    }
    
    @Override
    public String getCallbackPattern() {
        return "confirm_terminate_instances";
    }
}

/**
 * Terminate selected instances handler
 * 
 * @author yohann
 */
@Slf4j
@Component
class TerminateSelectedInstancesHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String callbackData = callbackQuery.getData();
        boolean preserveBootVolume = Boolean.parseBoolean(callbackData.split(":")[1]);
        
        long chatId = callbackQuery.getMessage().getChatId();
        InstanceSelectionStorage storage = InstanceSelectionStorage.getInstance();
        Set<String> selectedInstances = storage.getSelectedInstances(chatId);
        String ociCfgId = storage.getConfigContext(chatId);
        
        if (selectedInstances.isEmpty()) {
            try {
                telegramClient.execute(AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackQuery.getId())
                        .text("没有选中的实例")
                        .showAlert(true)
                        .build());
            } catch (TelegramApiException e) {
                log.error("Failed to answer callback query", e);
            }
            return null;
        }
        
        if (ociCfgId == null) {
            try {
                telegramClient.execute(AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackQuery.getId())
                        .text("配置上下文丢失")
                        .showAlert(true)
                        .build());
            } catch (TelegramApiException e) {
                log.error("Failed to answer callback query", e);
            }
            return null;
        }
        
        // Answer callback query
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQuery.getId())
                    .text("正在终止实例...")
                    .showAlert(false)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to answer callback query", e);
        }
        
        // Delete the confirmation message
        try {
            telegramClient.execute(org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(Math.toIntExact(callbackQuery.getMessage().getMessageId()))
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to delete message", e);
        }
        
        // Send processing message
        String processingMessage = String.format(
                "⏳ 正在终止 %d 个实例...\n\n" +
                "保留引导卷：%s\n\n" +
                "请稍候，任务已提交...",
                selectedInstances.size(),
                preserveBootVolume ? "是" : "否"
        );
        
        try {
            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(processingMessage)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to send processing message", e);
        }
        
        // Async terminate instances
        terminateInstancesAsync(ociCfgId, selectedInstances, preserveBootVolume, chatId, telegramClient);
        
        // Clear all data (selection, context, and cache) since we're done
        storage.clearAll(chatId);
        
        return null;
    }
    
    /**
     * Terminate instances asynchronously
     */
    private void terminateInstancesAsync(String ociCfgId, Set<String> instanceIds, 
                                        boolean preserveBootVolume, long chatId, 
                                        TelegramClient telegramClient) {
        
        CompletableFuture.runAsync(() -> {
            ISysService sysService = SpringUtil.getBean(ISysService.class);
            SysUserDTO sysUserDTO = sysService.getOciUser(ociCfgId);
            
            int successCount = 0;
            int failedCount = 0;
            StringBuilder resultMessage = new StringBuilder();
            
            // Terminate instances in parallel
            List<CompletableFuture<Void>> futures = instanceIds.stream()
                    .map(instanceId -> CompletableFuture.runAsync(() -> {
                        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
                            fetcher.terminateInstance(instanceId, preserveBootVolume, preserveBootVolume);
                            log.info("Successfully terminated instance: instanceId={}, preserveBootVolume={}", 
                                    instanceId, preserveBootVolume);
                        } catch (Exception e) {
                            log.error("Failed to terminate instance: instanceId={}", instanceId, e);
                            throw new RuntimeException(e);
                        }
                    }, VIRTUAL_EXECUTOR))
                    .toList();
            
            // Wait for all tasks to complete and count results
            for (int i = 0; i < futures.size(); i++) {
                try {
                    futures.get(i).join();
                    successCount++;
                } catch (Exception e) {
                    failedCount++;
                    log.error("Instance termination failed", e);
                }
            }
            
            // Build result message
            if (failedCount > 0) {
                resultMessage.append(String.format(
                        "✅ 成功下发终止实例任务，终止 %d 个实例\n❌ 失败 %d 个实例，请稍后刷新列表查看~\n\n保留引导卷：%s",
                        successCount, failedCount, preserveBootVolume ? "是" : "否"
                ));
            } else {
                resultMessage.append(String.format(
                        "✅ 已成功下发终止 %d 个实例任务！请稍后刷新列表查看~\n\n保留引导卷：%s",
                        successCount, preserveBootVolume ? "是" : "否"
                ));
            }
            
            // Send result message
            try {
                telegramClient.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text(resultMessage.toString())
                        .build());
            } catch (TelegramApiException e) {
                log.error("Failed to send result message: chatId={}", chatId, e);
            }
            
        }, VIRTUAL_EXECUTOR);
    }
    
    @Override
    public String getCallbackPattern() {
        return "terminate_instances:";
    }
}
