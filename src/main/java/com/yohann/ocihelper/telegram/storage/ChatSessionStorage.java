package com.yohann.ocihelper.telegram.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI Chat Session Storage
 * Thread-safe singleton storage for managing AI chat sessions
 * 
 * @author yohann
 */
public class ChatSessionStorage {
    
    private static final ChatSessionStorage INSTANCE = new ChatSessionStorage();
    
    /**
     * Chat session info with last used timestamp
     */
    private static class SessionInfo {
        private final List<String> history;
        private long lastUsed;
        
        public SessionInfo() {
            this.history = new ArrayList<>();
            this.lastUsed = System.currentTimeMillis();
        }
        
        public List<String> getHistory() {
            this.lastUsed = System.currentTimeMillis();
            return history;
        }
        
        public void addMessage(String message) {
            history.add(message);
            this.lastUsed = System.currentTimeMillis();
        }
        
        public long getLastUsed() {
            return lastUsed;
        }
    }
    
    // Store chat history: chatId -> SessionInfo
    private final Map<Long, SessionInfo> chatSessions = new ConcurrentHashMap<>();
    
    // Store session IDs: chatId -> sessionId
    private final Map<Long, String> sessionIds = new ConcurrentHashMap<>();
    
    // Store AI model selection: chatId -> model name
    private final Map<Long, String> selectedModels = new ConcurrentHashMap<>();
    
    // Store internet search enable status: chatId -> boolean
    private final Map<Long, Boolean> internetEnabled = new ConcurrentHashMap<>();
    
    // Max history messages per chat
    private static final int MAX_HISTORY_SIZE = 10;
    
    private ChatSessionStorage() {
    }
    
    public static ChatSessionStorage getInstance() {
        return INSTANCE;
    }
    
    /**
     * Add message to chat history
     * 
     * @param chatId chat ID
     * @param message message content
     */
    public void addMessage(long chatId, String message) {
        SessionInfo session = chatSessions.computeIfAbsent(chatId, k -> new SessionInfo());
        session.addMessage(message);
        
        // Keep only last MAX_HISTORY_SIZE messages
        List<String> history = session.getHistory();
        if (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }
    }
    
    /**
     * Get chat history
     * 
     * @param chatId chat ID
     * @return list of messages
     */
    public List<String> getHistory(long chatId) {
        SessionInfo session = chatSessions.get(chatId);
        if (session != null) {
            return new ArrayList<>(session.getHistory());
        }
        return new ArrayList<>();
    }
    
    /**
     * Clear chat history
     * 
     * @param chatId chat ID
     */
    public void clearHistory(long chatId) {
        chatSessions.remove(chatId);
        sessionIds.remove(chatId);
    }
    
    /**
     * Get or create session ID
     * 
     * @param chatId chat ID
     * @return session ID
     */
    public String getOrCreateSessionId(long chatId) {
        return sessionIds.computeIfAbsent(chatId, k -> "tg_session_" + chatId + "_" + System.currentTimeMillis());
    }
    
    /**
     * Set AI model for chat
     * 
     * @param chatId chat ID
     * @param model model name
     */
    public void setModel(long chatId, String model) {
        selectedModels.put(chatId, model);
    }
    
    /**
     * Get AI model for chat
     * 
     * @param chatId chat ID
     * @return model name, or default model if not set
     */
    public String getModel(long chatId) {
        return selectedModels.getOrDefault(chatId, "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B");
    }
    
    /**
     * Set internet search enable status
     * 
     * @param chatId chat ID
     * @param enabled enable status
     */
    public void setInternetEnabled(long chatId, boolean enabled) {
        internetEnabled.put(chatId, enabled);
    }
    
    /**
     * Get internet search enable status
     * 
     * @param chatId chat ID
     * @return enable status, default false
     */
    public boolean isInternetEnabled(long chatId) {
        return internetEnabled.getOrDefault(chatId, false);
    }
    
    /**
     * Clear all data for a chat
     * 
     * @param chatId chat ID
     */
    public void clearAll(long chatId) {
        chatSessions.remove(chatId);
        sessionIds.remove(chatId);
        selectedModels.remove(chatId);
        internetEnabled.remove(chatId);
    }
    
    /**
     * Clean up expired sessions (not used for more than 30 minutes)
     */
    public void cleanExpiredSessions() {
        long now = System.currentTimeMillis();
        long timeout = 30 * 60 * 1000; // 30 minutes
        
        // Clean expired chat sessions
        chatSessions.entrySet().removeIf(entry -> 
            now - entry.getValue().getLastUsed() > timeout
        );
        
        // Clean orphaned data
        sessionIds.entrySet().removeIf(entry -> !chatSessions.containsKey(entry.getKey()));
        selectedModels.entrySet().removeIf(entry -> !chatSessions.containsKey(entry.getKey()));
        internetEnabled.entrySet().removeIf(entry -> !chatSessions.containsKey(entry.getKey()));
    }
}
