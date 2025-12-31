package com.yohann.ocihelper.telegram.storage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH Connection Storage
 * Thread-safe singleton storage for managing SSH connection info
 * 
 * @author yohann
 */
public class SshConnectionStorage {
    
    private static final SshConnectionStorage INSTANCE = new SshConnectionStorage();
    
    /**
     * SSH Connection Info
     */
    public static class SshInfo {
        private String host;
        private int port;
        private String username;
        private String password;
        private long lastUsed;
        
        public SshInfo(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
            this.lastUsed = System.currentTimeMillis();
        }
        
        public String getHost() {
            return host;
        }
        
        public int getPort() {
            return port;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public long getLastUsed() {
            return lastUsed;
        }
        
        public void updateLastUsed() {
            this.lastUsed = System.currentTimeMillis();
        }
    }
    
    // Store SSH connection info: chatId -> SshInfo
    private final Map<Long, SshInfo> connections = new ConcurrentHashMap<>();
    
    private SshConnectionStorage() {
    }
    
    public static SshConnectionStorage getInstance() {
        return INSTANCE;
    }
    
    /**
     * Save SSH connection info
     * 
     * @param chatId chat ID
     * @param host SSH host
     * @param port SSH port
     * @param username SSH username
     * @param password SSH password
     */
    public void saveConnection(long chatId, String host, int port, String username, String password) {
        connections.put(chatId, new SshInfo(host, port, username, password));
    }
    
    /**
     * Get SSH connection info
     * 
     * @param chatId chat ID
     * @return SSH info or null if not found
     */
    public SshInfo getConnection(long chatId) {
        SshInfo info = connections.get(chatId);
        if (info != null) {
            info.updateLastUsed();
        }
        return info;
    }
    
    /**
     * Check if connection exists
     * 
     * @param chatId chat ID
     * @return true if connection exists
     */
    public boolean hasConnection(long chatId) {
        return connections.containsKey(chatId);
    }
    
    /**
     * Remove SSH connection info
     * 
     * @param chatId chat ID
     */
    public void removeConnection(long chatId) {
        connections.remove(chatId);
    }
    
    /**
     * Clear all connections
     */
    public void clearAll() {
        connections.clear();
    }
    
    /**
     * Clean up expired connections (not used for more than 30 minutes)
     */
    public void cleanExpiredConnections() {
        long now = System.currentTimeMillis();
        long timeout = 30 * 60 * 1000; // 30 minutes
        
        connections.entrySet().removeIf(entry -> 
            now - entry.getValue().getLastUsed() > timeout
        );
    }
}
