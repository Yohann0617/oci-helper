package com.yohann.ocihelper.telegram.storage;

import com.yohann.ocihelper.bean.response.oci.volume.BootVolumeListPage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telegram Bot boot volume selection storage
 * Thread-safe singleton storage for boot volume selection
 * 
 * @author yohann
 */
public class BootVolumeSelectionStorage {
    
    private static final BootVolumeSelectionStorage INSTANCE = new BootVolumeSelectionStorage();
    
    // Mapping: chatId -> selected boot volume ID set
    private final Map<Long, Set<String>> selections = new ConcurrentHashMap<>();
    
    // Mapping: chatId -> ociCfgId (to track which config's boot volumes are being managed)
    private final Map<Long, String> configContext = new ConcurrentHashMap<>();
    
    // Mapping: chatId -> List of boot volumes (for index-based access)
    private final Map<Long, List<BootVolumeListPage.BootVolumeInfo>> volumeCache = new ConcurrentHashMap<>();
    
    private BootVolumeSelectionStorage() {
    }
    
    public static BootVolumeSelectionStorage getInstance() {
        return INSTANCE;
    }
    
    /**
     * Set boot volume cache for a chat
     * 
     * @param chatId Chat ID
     * @param volumes List of boot volumes
     */
    public void setVolumeCache(long chatId, List<BootVolumeListPage.BootVolumeInfo> volumes) {
        volumeCache.put(chatId, new ArrayList<>(volumes));
    }
    
    /**
     * Get boot volume by index
     * 
     * @param chatId Chat ID
     * @param index Volume index
     * @return BootVolumeInfo or null if not found
     */
    public BootVolumeListPage.BootVolumeInfo getVolumeByIndex(long chatId, int index) {
        List<BootVolumeListPage.BootVolumeInfo> volumes = volumeCache.get(chatId);
        if (volumes != null && index >= 0 && index < volumes.size()) {
            return volumes.get(index);
        }
        return null;
    }
    
    /**
     * Get all cached boot volumes
     * 
     * @param chatId Chat ID
     * @return List of boot volumes or empty list
     */
    public List<BootVolumeListPage.BootVolumeInfo> getCachedVolumes(long chatId) {
        return volumeCache.getOrDefault(chatId, new ArrayList<>());
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
     * Toggle boot volume selection state
     * 
     * @param chatId Chat ID
     * @param volumeId Volume ID
     * @return true if selected, false if deselected
     */
    public boolean toggleVolume(long chatId, String volumeId) {
        Set<String> selected = selections.computeIfAbsent(chatId, k -> new HashSet<>());
        
        if (selected.contains(volumeId)) {
            selected.remove(volumeId);
            return false;
        } else {
            selected.add(volumeId);
            return true;
        }
    }
    
    /**
     * Select boot volume
     * 
     * @param chatId Chat ID
     * @param volumeId Volume ID
     */
    public void selectVolume(long chatId, String volumeId) {
        selections.computeIfAbsent(chatId, k -> new HashSet<>()).add(volumeId);
    }
    
    /**
     * Deselect boot volume
     * 
     * @param chatId Chat ID
     * @param volumeId Volume ID
     */
    public void deselectVolume(long chatId, String volumeId) {
        Set<String> selected = selections.get(chatId);
        if (selected != null) {
            selected.remove(volumeId);
        }
    }
    
    /**
     * Check if boot volume is selected
     * 
     * @param chatId Chat ID
     * @param volumeId Volume ID
     * @return true if selected
     */
    public boolean isSelected(long chatId, String volumeId) {
        Set<String> selected = selections.get(chatId);
        return selected != null && selected.contains(volumeId);
    }
    
    /**
     * Get selected boot volumes
     * 
     * @param chatId Chat ID
     * @return Set of selected volume IDs
     */
    public Set<String> getSelectedVolumes(long chatId) {
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
        volumeCache.remove(chatId);
    }
}
