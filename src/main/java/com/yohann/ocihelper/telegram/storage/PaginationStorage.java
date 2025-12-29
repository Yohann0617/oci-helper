package com.yohann.ocihelper.telegram.storage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telegram Bot 分页存储
 * 线程安全的单例存储，用于管理分页状态
 * 
 * @author yohann
 */
public class PaginationStorage {
    
    private static final PaginationStorage INSTANCE = new PaginationStorage();
    
    // 映射: chatId + pageType -> 当前页码
    private final Map<String, Integer> pageNumbers = new ConcurrentHashMap<>();
    
    // 默认每页大小
    public static final int DEFAULT_PAGE_SIZE = 8;
    
    private PaginationStorage() {
    }
    
    public static PaginationStorage getInstance() {
        return INSTANCE;
    }
    
    /**
     * 构建存储键
     * 
     * @param chatId 聊天ID
     * @param pageType 页面类型（如 "config_list", "task_management"）
     * @return 存储键
     */
    private String buildKey(long chatId, String pageType) {
        return chatId + ":" + pageType;
    }
    
    /**
     * 获取当前页码
     * 
     * @param chatId 聊天ID
     * @param pageType 页面类型
     * @return 当前页码（从0开始）
     */
    public int getCurrentPage(long chatId, String pageType) {
        return pageNumbers.getOrDefault(buildKey(chatId, pageType), 0);
    }
    
    /**
     * 设置当前页码
     * 
     * @param chatId 聊天ID
     * @param pageType 页面类型
     * @param page 页码
     */
    public void setCurrentPage(long chatId, String pageType, int page) {
        pageNumbers.put(buildKey(chatId, pageType), page);
    }
    
    /**
     * 重置页码为0
     * 
     * @param chatId 聊天ID
     * @param pageType 页面类型
     */
    public void resetPage(long chatId, String pageType) {
        pageNumbers.remove(buildKey(chatId, pageType));
    }
    
    /**
     * 跳转到下一页
     * 
     * @param chatId 聊天ID
     * @param pageType 页面类型
     * @param totalPages 总页数
     * @return 新页码
     */
    public int nextPage(long chatId, String pageType, int totalPages) {
        int current = getCurrentPage(chatId, pageType);
        int next = Math.min(current + 1, totalPages - 1);
        setCurrentPage(chatId, pageType, next);
        return next;
    }
    
    /**
     * 跳转到上一页
     * 
     * @param chatId 聊天ID
     * @param pageType 页面类型
     * @return 新页码
     */
    public int previousPage(long chatId, String pageType) {
        int current = getCurrentPage(chatId, pageType);
        int prev = Math.max(current - 1, 0);
        setCurrentPage(chatId, pageType, prev);
        return prev;
    }
    
    /**
     * 计算总页数
     * 
     * @param totalItems 总项数
     * @param pageSize 每页大小
     * @return 总页数
     */
    public static int calculateTotalPages(int totalItems, int pageSize) {
        return (int) Math.ceil((double) totalItems / pageSize);
    }
    
    /**
     * 获取当前页的起始索引
     * 
     * @param page 页码（从0开始）
     * @param pageSize 每页大小
     * @return 起始索引
     */
    public static int getStartIndex(int page, int pageSize) {
        return page * pageSize;
    }
    
    /**
     * 获取当前页的结束索引
     * 
     * @param page 页码（从0开始）
     * @param pageSize 每页大小
     * @param totalItems 总项数
     * @return 结束索引（不包含）
     */
    public static int getEndIndex(int page, int pageSize, int totalItems) {
        return Math.min((page + 1) * pageSize, totalItems);
    }
    
    /**
     * 清除某个聊天的所有分页数据
     * 
     * @param chatId 聊天ID
     */
    public void clearChat(long chatId) {
        String prefix = chatId + ":";
        pageNumbers.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
