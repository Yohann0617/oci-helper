package com.yohann.ocihelper.telegram.task;

import com.yohann.ocihelper.telegram.storage.ChatSessionStorage;
import com.yohann.ocihelper.telegram.storage.SshConnectionStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Telegram Bot Scheduled Tasks
 * Clean up expired connections and sessions
 * 
 * @author yohann
 */
@Slf4j
@Component
public class TelegramCleanupTask {
    
    /**
     * Clean up expired SSH connections and AI chat sessions
     * Runs every 30 minutes
     */
    @Scheduled(cron = "0 */30 * * * ?")
    public void cleanupExpiredData() {
        try {
            // Clean SSH connections
            SshConnectionStorage.getInstance().cleanExpiredConnections();
            log.info("Cleaned up expired SSH connections");
            
            // Clean AI chat sessions
            ChatSessionStorage.getInstance().cleanExpiredSessions();
            log.info("Cleaned up expired AI chat sessions");
        } catch (Exception e) {
            log.error("Failed to clean up expired data", e);
        }
    }
}
