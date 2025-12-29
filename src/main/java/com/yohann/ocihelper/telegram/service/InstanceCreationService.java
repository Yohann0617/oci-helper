package com.yohann.ocihelper.telegram.service;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.bean.params.oci.instance.CreateInstanceParams;
import com.yohann.ocihelper.service.IOciService;
import com.yohann.ocihelper.service.IOciUserService;
import com.yohann.ocihelper.telegram.model.InstancePlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.concurrent.CompletableFuture;

import static com.yohann.ocihelper.config.VirtualThreadConfig.VIRTUAL_EXECUTOR;

/**
 * Telegram Bot instance creation service
 * 
 * @author yohann
 */
@Slf4j
@Service
public class InstanceCreationService {
    
    /**
     * Create instance asynchronously
     * 
     * @param userId User ID
     * @param plan Instance plan
     * @param chatId Telegram chat ID
     * @param telegramClient Telegram client
     */
    public void createInstanceAsync(String userId, InstancePlan plan, long chatId, TelegramClient telegramClient) {
        CompletableFuture.runAsync(() -> {
            try {
                createInstance(userId, plan);
                
                // Send success message
                telegramClient.execute(SendMessage.builder()
                        .chatId(chatId)
                        .text("✅ 开机任务已成功提交！\n\n可以通过【任务管理】查看任务进度。")
                        .build());
                
                log.info("Instance creation task submitted successfully: userId={}, chatId={}", userId, chatId);
                
            } catch (Exception e) {
                log.error("Failed to create instance: userId={}, chatId={}", userId, chatId, e);
                
                // Send error message
                try {
                    String errorMessage = e.getMessage();
                    if (errorMessage == null || errorMessage.isEmpty()) {
                        errorMessage = "未知错误";
                    }
                    
                    telegramClient.execute(SendMessage.builder()
                            .chatId(chatId)
                            .text("❌ 开机任务提交失败：" + errorMessage)
                            .build());
                } catch (TelegramApiException ex) {
                    log.error("Failed to send error message: chatId={}", chatId, ex);
                }
            }
        }, VIRTUAL_EXECUTOR);
    }
    
    /**
     * Create instance task by calling IOciService.createInstance
     * 
     * @param userId User ID
     * @param plan Instance plan
     * @throws RuntimeException if user config not found or creation failed
     */
    private void createInstance(String userId, InstancePlan plan) {
        IOciUserService userService = SpringUtil.getBean(IOciUserService.class);
        IOciService ociService = SpringUtil.getBean(IOciService.class);
        
        // Validate user exists
        OciUser user = userService.getById(userId);
        if (user == null) {
            throw new RuntimeException("配置不存在，用户ID: " + userId);
        }
        
        // Generate random password if not provided
        String password = plan.getRootPassword();
        if (password == null || password.isEmpty()) {
            password = RandomUtil.randomString(16);
            log.debug("Generated random password for instance creation");
        }
        
        // Build CreateInstanceParams
        CreateInstanceParams params = new CreateInstanceParams();
        params.setUserId(userId);
        params.setOcpus(String.valueOf(plan.getOcpus()));
        params.setMemory(String.valueOf(plan.getMemory()));
        params.setDisk(plan.getDisk());
        params.setArchitecture(plan.getArchitecture());
        params.setInterval(plan.getInterval());
        params.setCreateNumbers(plan.getCreateNumbers());
        params.setOperationSystem(plan.getOperationSystem());
        params.setRootPassword(password);
        
        // Call IOciService.createInstance method to create instance task
        ociService.createInstance(params);
        
        log.info("Successfully called IOciService.createInstance: userId={}, ocpus={}, memory={}, disk={}, arch={}", 
                 userId, plan.getOcpus(), plan.getMemory(), plan.getDisk(), plan.getArchitecture());
    }
}
