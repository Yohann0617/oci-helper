package com.yohann.ocihelper.telegram.storage;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telegram Bot 任务选择存储
 * 线程安全的单例存储，用于任务选择
 * 
 * @author yohann
 */
public class TaskSelectionStorage {
    
    private static final TaskSelectionStorage INSTANCE = new TaskSelectionStorage();
    
    // 映射: chatId -> 选中的任务ID集合
    private final Map<Long, Set<String>> selections = new ConcurrentHashMap<>();
    
    private TaskSelectionStorage() {
    }
    
    public static TaskSelectionStorage getInstance() {
        return INSTANCE;
    }
    
    /**
     * 切换任务选择状态
     * 
     * @param chatId 聊天ID
     * @param taskId 任务ID
     * @return true表示已选中，false表示已取消选中
     */
    public boolean toggleTask(long chatId, String taskId) {
        Set<String> selected = selections.computeIfAbsent(chatId, k -> new HashSet<>());
        
        if (selected.contains(taskId)) {
            selected.remove(taskId);
            return false;
        } else {
            selected.add(taskId);
            return true;
        }
    }
    
    /**
     * 选中任务
     * 
     * @param chatId 聊天ID
     * @param taskId 任务ID
     */
    public void selectTask(long chatId, String taskId) {
        selections.computeIfAbsent(chatId, k -> new HashSet<>()).add(taskId);
    }
    
    /**
     * 取消选中任务
     * 
     * @param chatId 聊天ID
     * @param taskId 任务ID
     */
    public void deselectTask(long chatId, String taskId) {
        Set<String> selected = selections.get(chatId);
        if (selected != null) {
            selected.remove(taskId);
        }
    }
    
    /**
     * 检查任务是否已选中
     * 
     * @param chatId 聊天ID
     * @param taskId 任务ID
     * @return 如果已选中返回true
     */
    public boolean isSelected(long chatId, String taskId) {
        Set<String> selected = selections.get(chatId);
        return selected != null && selected.contains(taskId);
    }
    
    /**
     * 获取选中的任务
     * 
     * @param chatId 聊天ID
     * @return 选中的任务ID集合
     */
    public Set<String> getSelectedTasks(long chatId) {
        return new HashSet<>(selections.getOrDefault(chatId, new HashSet<>()));
    }
    
    /**
     * 清除聊天的选择
     * 
     * @param chatId 聊天ID
     */
    public void clearSelection(long chatId) {
        selections.remove(chatId);
    }
}
