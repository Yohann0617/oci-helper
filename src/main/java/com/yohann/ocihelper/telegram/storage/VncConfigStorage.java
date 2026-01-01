package com.yohann.ocihelper.telegram.storage;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VNC configuration session storage
 * Manages temporary VNC configuration state during setup
 * 
 * @author yohann
 */
@Slf4j
public class VncConfigStorage {
    
    private static final VncConfigStorage INSTANCE = new VncConfigStorage();
    
    /**
     * Stores chat IDs that are in the process of configuring VNC
     * Key: chatId, Value: true if waiting for VNC URL input
     */
    private final Map<Long, Boolean> configuringSessions = new ConcurrentHashMap<>();
    
    private VncConfigStorage() {
    }
    
    public static VncConfigStorage getInstance() {
        return INSTANCE;
    }
    
    /**
     * Mark a chat as configuring VNC
     * 
     * @param chatId the chat ID
     */
    public void startConfiguring(long chatId) {
        configuringSessions.put(chatId, true);
        log.debug("Started VNC configuration for chatId: {}", chatId);
    }
    
    /**
     * Check if a chat is configuring VNC
     * 
     * @param chatId the chat ID
     * @return true if configuring
     */
    public boolean isConfiguring(long chatId) {
        return configuringSessions.getOrDefault(chatId, false);
    }
    
    /**
     * Stop VNC configuration for a chat
     * 
     * @param chatId the chat ID
     */
    public void stopConfiguring(long chatId) {
        configuringSessions.remove(chatId);
        log.debug("Stopped VNC configuration for chatId: {}", chatId);
    }
    
    /**
     * Clear all sessions (for cleanup)
     */
    public void clearAll() {
        configuringSessions.clear();
        log.info("Cleared all VNC configuration sessions");
    }
}
