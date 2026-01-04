package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.service.IOciUserService;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import com.yohann.ocihelper.telegram.storage.PaginationStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置列表分页导航处理器
 * 
 * @author yohann
 */
@Slf4j
@Component
public class ConfigPageNavigationHandler extends AbstractCallbackHandler {
    
    private static final String PAGE_TYPE = "config_list";
    private static final int PAGE_SIZE = 8;
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String data = callbackQuery.getData();
        boolean isNext = data.equals("config_page_next");
        
        IOciUserService userService = SpringUtil.getBean(IOciUserService.class);
        List<OciUser> userList = userService.list(new LambdaQueryWrapper<OciUser>()
                .select(OciUser::getId, OciUser::getUsername, OciUser::getOciRegion));
        
        if (CollectionUtil.isEmpty(userList)) {
            return buildEditMessage(
                    callbackQuery,
                    "❌ 暂无配置信息，请先添加 OCI 配置",
                    new InlineKeyboardMarkup(KeyboardBuilder.buildMainMenu())
            );
        }
        
        long chatId = callbackQuery.getMessage().getChatId();
        PaginationStorage paginationStorage = PaginationStorage.getInstance();
        
        int totalPages = PaginationStorage.calculateTotalPages(userList.size(), PAGE_SIZE);
        
        // 更新页码
        if (isNext) {
            paginationStorage.nextPage(chatId, PAGE_TYPE, totalPages);
        } else {
            paginationStorage.previousPage(chatId, PAGE_TYPE);
        }
        
        return buildConfigListMessage(callbackQuery, userList, chatId, paginationStorage);
    }
    
    /**
     * 构建配置列表消息
     */
    private BotApiMethod<? extends Serializable> buildConfigListMessage(
            CallbackQuery callbackQuery,
            List<OciUser> userList,
            long chatId,
            PaginationStorage paginationStorage) {
        
        int currentPage = paginationStorage.getCurrentPage(chatId, PAGE_TYPE);
        int totalPages = PaginationStorage.calculateTotalPages(userList.size(), PAGE_SIZE);
        int startIndex = PaginationStorage.getStartIndex(currentPage, PAGE_SIZE);
        int endIndex = PaginationStorage.getEndIndex(currentPage, PAGE_SIZE, userList.size());
        
        // 获取当前页的配置列表
        List<OciUser> pageUsers = userList.subList(startIndex, endIndex);
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        // 添加配置按钮（每行2个）
        List<InlineKeyboardRow> configRows = buildConfigRows(pageUsers);
        keyboard.addAll(configRows);
        
        // 添加分页按钮
        if (totalPages > 1) {
            keyboard.add(KeyboardBuilder.buildPaginationRow(
                    currentPage,
                    totalPages,
                    "config_page_prev",
                    "config_page_next"
            ));
        }
        
        // 添加导航按钮
        keyboard.add(KeyboardBuilder.buildBackToMainMenuRow());
        keyboard.add(KeyboardBuilder.buildCancelRow());
        
        String message = String.format(
                "【配置列表】\n\n共 %d 个配置，当前第 %d/%d 页\n请选择需要操作的配置：",
                userList.size(),
                currentPage + 1,
                totalPages
        );
        
        return buildEditMessage(callbackQuery, message, new InlineKeyboardMarkup(keyboard));
    }
    
    /**
     * 构建配置按钮行
     */
    private List<InlineKeyboardRow> buildConfigRows(List<OciUser> userList) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (int i = 0; i < userList.size(); i += 2) {
            InlineKeyboardRow row = new InlineKeyboardRow();
            
            OciUser user1 = userList.get(i);
            row.add(KeyboardBuilder.button(
                    String.format("%s [%s]", user1.getUsername(), user1.getOciRegion()),
                    "select_config:" + user1.getId()
            ));
            
            if (i + 1 < userList.size()) {
                OciUser user2 = userList.get(i + 1);
                row.add(KeyboardBuilder.button(
                        String.format("%s [%s]", user2.getUsername(), user2.getOciRegion()),
                        "select_config:" + user2.getId()
                ));
            }
            
            rows.add(row);
        }
        return rows;
    }
    
    @Override
    public String getCallbackPattern() {
        return "config_page_";
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        return callbackData != null && 
               (callbackData.equals("config_page_prev") || callbackData.equals("config_page_next"));
    }
}
