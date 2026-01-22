package com.yohann.ocihelper.service;

import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * IP Security Service
 * Manages IP blacklist and defense mode
 * 
 * @author yohann
 */
@Slf4j
@Service
public class IpSecurityService {
    
    // Dedicated cache for IP blacklist
    private final CustomExpiryGuavaCache<String, String> ipBlacklistCache;
    
    // Maintain a separate set for tracking all blacklist entries
    private final Set<String> blacklistEntries = ConcurrentHashMap.newKeySet();
    
    // Login failure tracking: IP -> failure count
    private final CustomExpiryGuavaCache<String, Integer> loginFailureCache;
    
    // Max login failures before auto-blacklist
    private static final int MAX_LOGIN_FAILURES = 5;
    
    // Login failure tracking window (15 minutes)
    private static final long FAILURE_TRACKING_WINDOW_MS = 15 * 60 * 1000;
    
    // Defense mode flag - when true, all IPs are blocked
    private volatile boolean defenseMode = false;
    
    // Pattern for IP address validation
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    
    // Pattern for IP range (CIDR notation)
    private static final Pattern CIDR_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/(3[0-2]|[12]?[0-9])$"
    );
    
    public IpSecurityService() {
        // Create dedicated cache instances for IP security
        this.ipBlacklistCache = new CustomExpiryGuavaCache<>();
        this.loginFailureCache = new CustomExpiryGuavaCache<>();
        log.info("IpSecurityService initialized with dedicated cache instances");
    }
    
    /**
     * Check if IP is allowed to access
     * 
     * @param ip IP address to check
     * @return true if allowed, false if blocked
     */
    public boolean isIpAllowed(String ip) {
        if (ip == null || ip.isEmpty()) {
            log.warn("isIpAllowed called with null/empty IP");
            return false;
        }
        
        // If defense mode is enabled, block all IPs
        if (defenseMode) {
            log.info("IP blocked by defense mode: {}", ip);
            return false;
        }
        
        // Check if IP is in blacklist
        if (isIpBlacklisted(ip)) {
            log.info("IP blocked by blacklist: {}", ip);
            return false;
        }
        
        return true;
    }
    
    /**
     * Check if IP is in blacklist (supports CIDR ranges)
     * 
     * @param ip IP address to check
     * @return true if blacklisted
     */
    public boolean isIpBlacklisted(String ip) {
        // Direct match
        String cachedValue = ipBlacklistCache.get(ip);
        if (cachedValue != null) {
            return true;
        }
        
        // Also check the Set
        if (blacklistEntries.contains(ip)) {
            return true;
        }
        
        // Check CIDR ranges
        List<String> allBlacklistEntries = getAllBlacklistEntries();
        for (String entry : allBlacklistEntries) {
            if (entry.contains("/")) {
                // CIDR range
                if (isIpInRange(ip, entry)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Add IP or IP range to blacklist
     * 
     * @param ipOrRange IP address or CIDR range (e.g., "192.168.1.100" or "192.168.1.0/24")
     * @return true if added successfully, false if invalid format
     */
    public boolean addToBlacklist(String ipOrRange) {
        if (ipOrRange == null || ipOrRange.isEmpty()) {
            log.warn("Attempted to add null or empty IP to blacklist");
            return false;
        }
        
        ipOrRange = ipOrRange.trim();
        
        // Validate format for IPv4
        if (!isValidIpOrRange(ipOrRange)) {
            // For auto-blacklist from login failures, accept any format (IPv6, localhost, etc.)
            // We'll add it anyway but log a warning
            log.warn("IP format does not match IPv4 pattern, adding anyway: {}", ipOrRange);
        }
        
        // Add to cache with no expiry (use a very long time)
        ipBlacklistCache.put(ipOrRange, ipOrRange, Long.MAX_VALUE);
        
        // Also add to the tracking set
        blacklistEntries.add(ipOrRange);
        
        log.info("Added to blacklist: {} (total entries: {})", ipOrRange, blacklistEntries.size());
        return true;
    }
    
    /**
     * Remove IP or IP range from blacklist
     * 
     * @param ipOrRange IP address or CIDR range
     * @return true if removed successfully
     */
    public boolean removeFromBlacklist(String ipOrRange) {
        if (ipOrRange == null || ipOrRange.isEmpty()) {
            return false;
        }
        
        ipBlacklistCache.remove(ipOrRange);
        blacklistEntries.remove(ipOrRange);
        
        log.info("Removed from blacklist: {} (remaining entries: {})", ipOrRange, blacklistEntries.size());
        return true;
    }
    
    /**
     * Clear all blacklist entries
     */
    public void clearBlacklist() {
        ipBlacklistCache.cleanUp();
        blacklistEntries.clear();
        log.info("Blacklist cleared");
    }
    
    /**
     * Get all blacklist entries
     * 
     * @return list of blacklisted IPs and ranges
     */
    public List<String> getAllBlacklistEntries() {
        return new ArrayList<>(blacklistEntries);
    }
    
    /**
     * Enable defense mode (block all IPs)
     */
    public void enableDefenseMode() {
        this.defenseMode = true;
        log.warn("Defense mode ENABLED - all IPs are now blocked");
    }
    
    /**
     * Disable defense mode
     */
    public void disableDefenseMode() {
        this.defenseMode = false;
        log.info("Defense mode DISABLED");
    }
    
    /**
     * Check if defense mode is enabled
     * 
     * @return true if enabled
     */
    public boolean isDefenseModeEnabled() {
        return defenseMode;
    }
    
    /**
     * Toggle defense mode
     * 
     * @return new state (true if now enabled)
     */
    public boolean toggleDefenseMode() {
        defenseMode = !defenseMode;
        log.info("Defense mode toggled: {}", defenseMode ? "ENABLED" : "DISABLED");
        return defenseMode;
    }
    
    /**
     * Validate IP address or CIDR range format
     * 
     * @param ipOrRange IP or range string
     * @return true if valid
     */
    private boolean isValidIpOrRange(String ipOrRange) {
        if (ipOrRange.contains("/")) {
            return CIDR_PATTERN.matcher(ipOrRange).matches();
        } else {
            return IP_PATTERN.matcher(ipOrRange).matches();
        }
    }
    
    /**
     * Check if IP is in CIDR range
     * 
     * @param ip IP address to check
     * @param cidr CIDR notation (e.g., "192.168.1.0/24")
     * @return true if IP is in range
     */
    private boolean isIpInRange(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String baseIp = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);
            
            long ipLong = ipToLong(ip);
            long baseIpLong = ipToLong(baseIp);
            
            // Calculate network mask
            long mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
            
            // Check if IP is in the same network
            return (ipLong & mask) == (baseIpLong & mask);
            
        } catch (Exception e) {
            log.error("Failed to check IP in range: ip={}, cidr={}", ip, cidr, e);
            return false;
        }
    }
    
    /**
     * Convert IP address string to long
     * 
     * @param ip IP address
     * @return long representation
     */
    private long ipToLong(String ip) {
        String[] octets = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result |= (Long.parseLong(octets[i]) << (24 - (8 * i)));
        }
        return result & 0xFFFFFFFFL;
    }
    
    /**
     * Record login failure and auto-blacklist if threshold exceeded
     * 
     * @param ip IP address that failed login
     * @return true if IP was auto-blacklisted
     */
    public boolean recordLoginFailure(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        // Get current failure count
        Integer currentFailures = loginFailureCache.get(ip);
        int newFailureCount = (currentFailures == null ? 0 : currentFailures) + 1;
        
        // Update failure count with expiry window
        loginFailureCache.put(ip, newFailureCount, FAILURE_TRACKING_WINDOW_MS);
        
        log.warn("Login failure recorded for IP: {}, failure count: {}/{}", 
                ip, newFailureCount, MAX_LOGIN_FAILURES);
        
        // Check if threshold exceeded
        if (newFailureCount >= MAX_LOGIN_FAILURES) {
            // Auto-blacklist the IP
            boolean added = addToBlacklist(ip);
            if (added) {
                log.error("IP {} has been AUTO-BLACKLISTED due to {} failed login attempts", 
                        ip, newFailureCount);
                // Clear failure count as IP is now blacklisted
                loginFailureCache.remove(ip);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Clear login failure count for an IP (called on successful login)
     * 
     * @param ip IP address
     */
    public void clearLoginFailures(String ip) {
        if (ip != null && !ip.isEmpty()) {
            loginFailureCache.remove(ip);
            log.debug("Login failure count cleared for IP: {}", ip);
        }
    }
    
    /**
     * Get current login failure count for an IP
     * 
     * @param ip IP address
     * @return failure count
     */
    public int getLoginFailureCount(String ip) {
        if (ip == null || ip.isEmpty()) {
            return 0;
        }
        Integer count = loginFailureCache.get(ip);
        return count == null ? 0 : count;
    }
    
    /**
     * Get statistics
     * 
     * @return statistics map
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        List<String> entries = getAllBlacklistEntries();
        
        long ipCount = entries.stream().filter(e -> !e.contains("/")).count();
        long rangeCount = entries.stream().filter(e -> e.contains("/")).count();
        
        stats.put("totalEntries", entries.size());
        stats.put("ipCount", ipCount);
        stats.put("rangeCount", rangeCount);
        stats.put("defenseMode", defenseMode);
        stats.put("maxLoginFailures", MAX_LOGIN_FAILURES);
        stats.put("failureTrackingWindowMinutes", FAILURE_TRACKING_WINDOW_MS / 60000);
        
        return stats;
    }
}
