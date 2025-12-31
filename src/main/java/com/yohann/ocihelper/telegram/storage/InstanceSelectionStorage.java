package com.yohann.ocihelper.telegram.storage;

import com.yohann.ocihelper.bean.dto.SysUserDTO;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telegram Bot instance selection storage
 * Thread-safe singleton storage for instance selection
 * 
 * @author yohann
 */
public class InstanceSelectionStorage {
    
    private static final InstanceSelectionStorage INSTANCE = new InstanceSelectionStorage();
    
    // Mapping: chatId -> selected instance ID set
    private final Map<Long, Set<String>> selections = new ConcurrentHashMap<>();
    
    // Mapping: chatId -> ociCfgId (to track which config's instances are being managed)
    private final Map<Long, String> configContext = new ConcurrentHashMap<>();
    
    // Mapping: chatId -> List of instances (for index-based access)
    private final Map<Long, List<SysUserDTO.CloudInstance>> instanceCache = new ConcurrentHashMap<>();
    
    private InstanceSelectionStorage() {
    }
    
    public static InstanceSelectionStorage getInstance() {
        return INSTANCE;
    }
    
    /**
     * Set instance cache for a chat
     * 
     * @param chatId Chat ID
     * @param instances List of instances
     */
    public void setInstanceCache(long chatId, List<SysUserDTO.CloudInstance> instances) {
        instanceCache.put(chatId, new ArrayList<>(instances));
    }
    
    /**
     * Get instance by index
     * 
     * @param chatId Chat ID
     * @param index Instance index
     * @return CloudInstance or null if not found
     */
    public SysUserDTO.CloudInstance getInstanceByIndex(long chatId, int index) {
        List<SysUserDTO.CloudInstance> instances = instanceCache.get(chatId);
        if (instances != null && index >= 0 && index < instances.size()) {
            return instances.get(index);
        }
        return null;
    }
    
    /**
     * Get all cached instances
     * 
     * @param chatId Chat ID
     * @return List of instances or empty list
     */
    public List<SysUserDTO.CloudInstance> getCachedInstances(long chatId) {
        return instanceCache.getOrDefault(chatId, new ArrayList<>());
    }
    
    /**
     * Set config context for a chat
     * 
     * @param chatId Chat ID
     * @param ociCfgId OCI config ID
     */
    public void setConfigContext(long chatId, String ociCfgId) {
        configContext.put(chatId, ociCfgId);
    }
    
    /**
     * Get config context for a chat
     * 
     * @param chatId Chat ID
     * @return OCI config ID, or null if not set
     */
    public String getConfigContext(long chatId) {
        return configContext.get(chatId);
    }
    
    /**
     * Toggle instance selection state
     * 
     * @param chatId Chat ID
     * @param instanceId Instance ID
     * @return true if selected, false if deselected
     */
    public boolean toggleInstance(long chatId, String instanceId) {
        Set<String> selected = selections.computeIfAbsent(chatId, k -> new HashSet<>());
        
        if (selected.contains(instanceId)) {
            selected.remove(instanceId);
            return false;
        } else {
            selected.add(instanceId);
            return true;
        }
    }
    
    /**
     * Select instance
     * 
     * @param chatId Chat ID
     * @param instanceId Instance ID
     */
    public void selectInstance(long chatId, String instanceId) {
        selections.computeIfAbsent(chatId, k -> new HashSet<>()).add(instanceId);
    }
    
    /**
     * Deselect instance
     * 
     * @param chatId Chat ID
     * @param instanceId Instance ID
     */
    public void deselectInstance(long chatId, String instanceId) {
        Set<String> selected = selections.get(chatId);
        if (selected != null) {
            selected.remove(instanceId);
        }
    }
    
    /**
     * Check if instance is selected
     * 
     * @param chatId Chat ID
     * @param instanceId Instance ID
     * @return true if selected
     */
    public boolean isSelected(long chatId, String instanceId) {
        Set<String> selected = selections.get(chatId);
        return selected != null && selected.contains(instanceId);
    }
    
    /**
     * Get selected instances
     * 
     * @param chatId Chat ID
     * @return Set of selected instance IDs
     */
    public Set<String> getSelectedInstances(long chatId) {
        return new HashSet<>(selections.getOrDefault(chatId, new HashSet<>()));
    }
    
    /**
     * Clear selection for a chat (only clear selections, keep context and cache)
     * 
     * @param chatId Chat ID
     */
    public void clearSelection(long chatId) {
        selections.remove(chatId);
    }
    
    /**
     * Clear all data for a chat (selection, context, and cache)
     * 
     * @param chatId Chat ID
     */
    public void clearAll(long chatId) {
        selections.remove(chatId);
        configContext.remove(chatId);
        instanceCache.remove(chatId);
    }
}
