package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.service.IInstanceService;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import com.yohann.ocihelper.telegram.storage.InstanceSelectionStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Instance management handler
 * 
 * @author yohann
 */
@Slf4j
@Component
public class InstanceManagementHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String callbackData = callbackQuery.getData();
        String ociCfgId = callbackData.split(":")[1];
        long chatId = callbackQuery.getMessage().getChatId();
        
        // Set config context
        InstanceSelectionStorage storage = InstanceSelectionStorage.getInstance();
        storage.setConfigContext(chatId, ociCfgId);
        storage.clearSelection(chatId); // Clear previous selections
        
        // Get running instances
        ISysService sysService = SpringUtil.getBean(ISysService.class);
        IInstanceService instanceService = SpringUtil.getBean(IInstanceService.class);
        
        try {
            SysUserDTO sysUserDTO = sysService.getOciUser(ociCfgId);
            List<SysUserDTO.CloudInstance> instances = instanceService.listRunningInstances(sysUserDTO);
            
            if (CollectionUtil.isEmpty(instances)) {
                return buildEditMessage(
                        callbackQuery,
                        "❌ 暂无运行中的实例",
                        new InlineKeyboardMarkup(List.of(
                                new InlineKeyboardRow(
                                        KeyboardBuilder.button("◀️ 返回", "select_config:" + ociCfgId)
                                ),
                                KeyboardBuilder.buildCancelRow()
                        ))
                );
            }
            
            // Cache instances for index-based access
            storage.setInstanceCache(chatId, instances);
            
            return buildInstanceListMessage(callbackQuery, instances, ociCfgId, chatId);
            
        } catch (Exception e) {
            log.error("Failed to list running instances for ociCfgId: {}", ociCfgId, e);
            return buildEditMessage(
                    callbackQuery,
                    "❌ 获取实例列表失败：" + e.getMessage(),
                    new InlineKeyboardMarkup(List.of(
                            new InlineKeyboardRow(
                                    KeyboardBuilder.button("◀️ 返回", "select_config:" + ociCfgId)
                            ),
                            KeyboardBuilder.buildCancelRow()
                    ))
            );
        }
    }
    
    /**
     * Build instance list message
     */
    private BotApiMethod<? extends Serializable> buildInstanceListMessage(
            CallbackQuery callbackQuery,
            List<SysUserDTO.CloudInstance> instances,
            String ociCfgId,
            long chatId) {
        
        InstanceSelectionStorage storage = InstanceSelectionStorage.getInstance();
        
        StringBuilder message = new StringBuilder("【实例管理】\n\n");
        message.append(String.format("共 %d 个运行中的实例：\n\n", instances.size()));
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        // Add instance buttons (using index instead of full instance ID)
        for (int i = 0; i < instances.size(); i++) {
            SysUserDTO.CloudInstance instance = instances.get(i);
            boolean isSelected = storage.isSelected(chatId, instance.getOcId());
            
            // Format public IPs
            String publicIps = CollectionUtil.isEmpty(instance.getPublicIp()) 
                    ? "无" 
                    : String.join(", ", instance.getPublicIp());
            
            message.append(String.format(
                    "%s %d. %s\n" +
                    "   区域: %s\n" +
                    "   ID: ...%s\n" +
                    "   Shape: %s\n" +
                    "   公网IP: %s\n\n",
                    isSelected ? "☑️" : "⬜",
                    i + 1,
                    instance.getName(),
                    instance.getRegion(),
                    instance.getOcId().substring(Math.max(0, instance.getOcId().length() - 8)), // Show last 8 chars
                    instance.getShape(),
                    publicIps
            ));
            
            // Add button (2 per row) - use index instead of full ID
            if (i % 2 == 0) {
                InlineKeyboardRow row = new InlineKeyboardRow();
                row.add(KeyboardBuilder.button(
                        String.format("%s 实例%d", isSelected ? "☑️" : "⬜", i + 1),
                        "toggle_instance:" + i  // Use index
                ));
                keyboard.add(row);
            } else {
                keyboard.get(keyboard.size() - 1).add(KeyboardBuilder.button(
                        String.format("%s 实例%d", isSelected ? "☑️" : "⬜", i + 1),
                        "toggle_instance:" + i  // Use index
                ));
            }
        }
        
        // Add batch operation buttons
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("✅ 全选", "select_all_instances"),
                KeyboardBuilder.button("⬜ 取消全选", "deselect_all_instances")
        ));
        
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("🔄 刷新列表", "refresh_instances")
        ));
        
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("🗑 终止选中的实例", "confirm_terminate_instances")
        ));
        
        // Back button
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回", "select_config:" + ociCfgId)
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());
        
        return buildEditMessage(
                callbackQuery,
                message.toString(),
                new InlineKeyboardMarkup(keyboard)
        );
    }
    
    @Override
    public String getCallbackPattern() {
        return "instance_management:";
    }
}

/**
 * Toggle instance selection handler
 * 
 * @author yohann
 */
@Slf4j
@Component
class ToggleInstanceHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String callbackData = callbackQuery.getData();
        int instanceIndex = Integer.parseInt(callbackData.split(":")[1]);
        long chatId = callbackQuery.getMessage().getChatId();
        
        InstanceSelectionStorage storage = InstanceSelectionStorage.getInstance();
        
        // Get instance by index
        SysUserDTO.CloudInstance instance = storage.getInstanceByIndex(chatId, instanceIndex);
        if (instance == null) {
            try {
                telegramClient.execute(AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackQuery.getId())
                        .text("实例不存在")
                        .showAlert(true)
                        .build());
            } catch (TelegramApiException e) {
                log.error("Failed to answer callback query", e);
            }
            return null;
        }
        
        boolean isSelected = storage.toggleInstance(chatId, instance.getOcId());
        
        // Answer callback query
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQuery.getId())
                    .text(isSelected ? "已选中" : "已取消选中")
                    .showAlert(false)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to answer callback query", e);
        }
        
        // Refresh instance list
        return refreshInstanceList(callbackQuery, chatId);
    }
    
    /**
     * Refresh instance list
     */
    public BotApiMethod<? extends Serializable> refreshInstanceList(CallbackQuery callbackQuery, long chatId) {
        InstanceSelectionStorage storage = InstanceSelectionStorage.getInstance();
        String ociCfgId = storage.getConfigContext(chatId);
        
        if (ociCfgId == null) {
            return buildEditMessage(
                    callbackQuery,
                    "❌ 配置上下文丢失，请重新进入实例管理",
                    new InlineKeyboardMarkup(KeyboardBuilder.buildMainMenu())
            );
        }
        
        // Get cached instances
        List<SysUserDTO.CloudInstance> instances = storage.getCachedInstances(chatId);
        
        if (CollectionUtil.isEmpty(instances)) {
            return buildEditMessage(
                    callbackQuery,
                    "❌ 实例缓存丢失，请重新进入实例管理",
                    new InlineKeyboardMarkup(List.of(
                            new InlineKeyboardRow(
                                    KeyboardBuilder.button("◀️ 返回", "select_config:" + ociCfgId)
                            ),
                            KeyboardBuilder.buildCancelRow()
                    ))
            );
        }
        
        return buildInstanceListMessage(callbackQuery, instances, ociCfgId, chatId);
    }
    
    /**
     * Build instance list message
     */
    private BotApiMethod<? extends Serializable> buildInstanceListMessage(
            CallbackQuery callbackQuery,
            List<SysUserDTO.CloudInstance> instances,
            String ociCfgId,
            long chatId) {
        
        InstanceSelectionStorage storage = InstanceSelectionStorage.getInstance();
        
        StringBuilder message = new StringBuilder("【实例管理】\n\n");
        message.append(String.format("共 %d 个运行中的实例：\n\n", instances.size()));
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        // Add instance buttons
        for (int i = 0; i < instances.size(); i++) {
            SysUserDTO.CloudInstance instance = instances.get(i);
            boolean isSelected = storage.isSelected(chatId, instance.getOcId());
            
            // Format public IPs
            String publicIps = CollectionUtil.isEmpty(instance.getPublicIp()) 
                    ? "无" 
                    : String.join(", ", instance.getPublicIp());
            
            message.append(String.format(
                    "%s %d. %s\n" +
                    "   区域: %s\n" +
                    "   ID: ...%s\n" +
                    "   Shape: %s\n" +
                    "   公网IP: %s\n\n",
                    isSelected ? "☑️" : "⬜",
                    i + 1,
                    instance.getName(),
                    instance.getRegion(),
                    instance.getOcId().substring(Math.max(0, instance.getOcId().length() - 8)),
                    instance.getShape(),
                    publicIps
            ));
            
            // Add button (2 per row) - use index instead of full ID
            if (i % 2 == 0) {
                InlineKeyboardRow row = new InlineKeyboardRow();
                row.add(KeyboardBuilder.button(
                        String.format("%s 实例%d", isSelected ? "☑️" : "⬜", i + 1),
                        "toggle_instance:" + i
                ));
                keyboard.add(row);
            } else {
                keyboard.get(keyboard.size() - 1).add(KeyboardBuilder.button(
                        String.format("%s 实例%d", isSelected ? "☑️" : "⬜", i + 1),
                        "toggle_instance:" + i
                ));
            }
        }
        
        // Add batch operation buttons
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("✅ 全选", "select_all_instances"),
                KeyboardBuilder.button("⬜ 取消全选", "deselect_all_instances")
        ));
        
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("🔄 刷新列表", "refresh_instances")
        ));
        
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("🗑 终止选中的实例", "confirm_terminate_instances")
        ));
        
        // Back button
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回", "select_config:" + ociCfgId)
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());
        
        return buildEditMessage(
                callbackQuery,
                message.toString(),
                new InlineKeyboardMarkup(keyboard)
        );
    }
    
    @Override
    public String getCallbackPattern() {
        return "toggle_instance:";
    }
}

/**
 * Select all instances handler
 * 
 * @author yohann
 */
@Slf4j
@Component
class SelectAllInstancesHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        InstanceSelectionStorage storage = InstanceSelectionStorage.getInstance();
        
        // Get cached instances and select all
        List<SysUserDTO.CloudInstance> instances = storage.getCachedInstances(chatId);
        
        if (CollectionUtil.isNotEmpty(instances)) {
            instances.forEach(instance -> storage.selectInstance(chatId, instance.getOcId()));
            
            // Answer callback query
            try {
                telegramClient.execute(AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackQuery.getId())
                        .text(String.format("已全选 %d 个实例", instances.size()))
                        .showAlert(false)
                        .build());
            } catch (TelegramApiException e) {
                log.error("Failed to answer callback query", e);
            }
        }
        
        // Refresh instance list
        ToggleInstanceHandler handler = SpringUtil.getBean(ToggleInstanceHandler.class);
        return handler.refreshInstanceList(callbackQuery, chatId);
    }
    
    @Override
    public String getCallbackPattern() {
        return "select_all_instances";
    }
}

/**
 * Deselect all instances handler
 * 
 * @author yohann
 */
@Slf4j
@Component
class DeselectAllInstancesHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        
        InstanceSelectionStorage storage = InstanceSelectionStorage.getInstance();
        storage.clearSelection(chatId);
        
        // Answer callback query
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQuery.getId())
                    .text("已取消所有选中")
                    .showAlert(false)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to answer callback query", e);
        }
        
        // Refresh instance list
        ToggleInstanceHandler handler = SpringUtil.getBean(ToggleInstanceHandler.class);
        return handler.refreshInstanceList(callbackQuery, chatId);
    }
    
    @Override
    public String getCallbackPattern() {
        return "deselect_all_instances";
    }
}

/**
 * Refresh instances handler
 * 
 * @author yohann
 */
@Slf4j
@Component
class RefreshInstancesHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        InstanceSelectionStorage storage = InstanceSelectionStorage.getInstance();
        String ociCfgId = storage.getConfigContext(chatId);
        
        if (ociCfgId == null) {
            try {
                telegramClient.execute(AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackQuery.getId())
                        .text("配置上下文丢失，请重新进入实例管理")
                        .showAlert(true)
                        .build());
            } catch (TelegramApiException e) {
                log.error("Failed to answer callback query", e);
            }
            return null;
        }
        
        // Answer callback query
        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQuery.getId())
                    .text("正在刷新实例列表...")
                    .showAlert(false)
                    .build());
        } catch (TelegramApiException e) {
            log.error("Failed to answer callback query", e);
        }
        
        // Get running instances
        ISysService sysService = SpringUtil.getBean(ISysService.class);
        IInstanceService instanceService = SpringUtil.getBean(IInstanceService.class);
        
        try {
            SysUserDTO sysUserDTO = sysService.getOciUser(ociCfgId);
            List<SysUserDTO.CloudInstance> instances = instanceService.listRunningInstances(sysUserDTO);
            
            if (CollectionUtil.isEmpty(instances)) {
                return buildEditMessage(
                        callbackQuery,
                        "❌ 暂无运行中的实例",
                        new InlineKeyboardMarkup(List.of(
                                new InlineKeyboardRow(
                                        KeyboardBuilder.button("◀️ 返回", "select_config:" + ociCfgId)
                                ),
                                KeyboardBuilder.buildCancelRow()
                        ))
                );
            }
            
            // Clear previous selections and update cache
            storage.clearSelection(chatId);
            storage.setInstanceCache(chatId, instances);
            
            // Build message with refresh timestamp
            InstanceSelectionStorage storage2 = InstanceSelectionStorage.getInstance();
            
            StringBuilder message = new StringBuilder("【实例管理】\n\n");
            message.append(String.format("共 %d 个运行中的实例：\n", instances.size()));
            message.append("🔄 刷新时间: ");
            message.append(java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            message.append("\n\n");
            
            List<InlineKeyboardRow> keyboard = new ArrayList<>();
            
            // Add instance buttons (using index instead of full instance ID)
            for (int i = 0; i < instances.size(); i++) {
                SysUserDTO.CloudInstance instance = instances.get(i);
                boolean isSelected = storage2.isSelected(chatId, instance.getOcId());
                
                // Format public IPs
                String publicIps = CollectionUtil.isEmpty(instance.getPublicIp()) 
                        ? "无" 
                        : String.join(", ", instance.getPublicIp());
                
                message.append(String.format(
                        "%s %d. %s\n" +
                        "   区域: %s\n" +
                        "   ID: ...%s\n" +
                        "   Shape: %s\n" +
                        "   公网IP: %s\n\n",
                        isSelected ? "☑️" : "⬜",
                        i + 1,
                        instance.getName(),
                        instance.getRegion(),
                        instance.getOcId().substring(Math.max(0, instance.getOcId().length() - 8)), // Show last 8 chars
                        instance.getShape(),
                        publicIps
                ));
                
                // Add button (2 per row) - use index instead of full ID
                if (i % 2 == 0) {
                    InlineKeyboardRow row = new InlineKeyboardRow();
                    row.add(KeyboardBuilder.button(
                            String.format("%s 实例%d", isSelected ? "☑️" : "⬜", i + 1),
                            "toggle_instance:" + i  // Use index
                    ));
                    keyboard.add(row);
                } else {
                    keyboard.get(keyboard.size() - 1).add(KeyboardBuilder.button(
                            String.format("%s 实例%d", isSelected ? "☑️" : "⬜", i + 1),
                            "toggle_instance:" + i  // Use index
                    ));
                }
            }
            
            // Add batch operation buttons
            keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("✅ 全选", "select_all_instances"),
                    KeyboardBuilder.button("⬜ 取消全选", "deselect_all_instances")
            ));
            
            keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("🔄 刷新列表", "refresh_instances")
            ));
            
            keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("🗑 终止选中的实例", "confirm_terminate_instances")
            ));
            
            // Back button
            keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("◀️ 返回", "select_config:" + ociCfgId)
            ));
            keyboard.add(KeyboardBuilder.buildCancelRow());
            
            return buildEditMessage(
                    callbackQuery,
                    message.toString(),
                    new InlineKeyboardMarkup(keyboard)
            );
            
        } catch (Exception e) {
            log.error("Failed to refresh instances for ociCfgId: {}", ociCfgId, e);
            
            try {
                telegramClient.execute(AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackQuery.getId())
                        .text("刷新失败：" + e.getMessage())
                        .showAlert(true)
                        .build());
            } catch (TelegramApiException ex) {
                log.error("Failed to answer callback query", ex);
            }
            
            return null;
        }
    }
    
    @Override
    public String getCallbackPattern() {
        return "refresh_instances";
    }
}
