package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.core.util.RuntimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.util.List;

import static java.lang.Math.toIntExact;

/**
 * 版本信息回调处理器
 * 
 * @author yohann
 */
@Slf4j
@Component
public class VersionInfoHandler extends VersionInfoBaseHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        try {
            return getVersionInfo(
                    callbackQuery.getMessage().getChatId(),
                    callbackQuery.getMessage().getMessageId(),
                    telegramClient
            );
        } catch (Exception e) {
            log.error("Handle version info error", e);
            return buildEditMessage(callbackQuery, "获取版本信息失败");
        }
    }
    
    @Override
    public String getCallbackPattern() {
        return "version_info";
    }
}

/**
 * 更新系统版本回调处理器
 * 
 * @author yohann
 */
@Slf4j
@Component
class UpdateSysVersionHandler extends VersionInfoBaseHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        long messageId = callbackQuery.getMessage().getMessageId();
        
        List<String> command = List.of("/bin/sh", "-c", "echo trigger > /app/oci-helper/update_version_trigger.flag");
        Process process = RuntimeUtil.exec(command.toArray(new String[0]));
        
        int exitCode = 0;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            log.error("Update version error", e);
        }
        
        try {
            if (exitCode == 0) {
                log.info("Start the version update task...");
                telegramClient.execute(DeleteMessage.builder()
                        .chatId(chatId)
                        .messageId(toIntExact(messageId))
                        .build());
                return SendMessage.builder()
                        .chatId(chatId)
                        .text("\uD83D\uDD04 正在更新 oci-helper 最新版本，请稍后...")
                        .build();
            } else {
                log.error("version update task exec error,exitCode:{}", exitCode);
                return SendMessage.builder()
                        .chatId(chatId)
                        .text("一键更新失败，请手动更新版本~")
                        .build();
            }
        } catch (TelegramApiException e) {
            log.error("TG Bot error", e);
            return null;
        }
    }
    
    @Override
    public String getCallbackPattern() {
        return "update_sys_version";
    }
}
