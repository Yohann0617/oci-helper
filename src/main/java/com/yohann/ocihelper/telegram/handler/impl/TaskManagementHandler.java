package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.bean.entity.OciCreateTask;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.service.IOciCreateTaskService;
import com.yohann.ocihelper.service.IOciUserService;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import com.yohann.ocihelper.telegram.storage.PaginationStorage;
import com.yohann.ocihelper.telegram.storage.TaskSelectionStorage;
import com.yohann.ocihelper.utils.CommonUtils;
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
import java.util.Map;
import java.util.stream.Collectors;

import static com.yohann.ocihelper.service.impl.OciServiceImpl.TEMP_MAP;

/**
 * 任务管理回调处理器
 * 
 * @author yohann
 */
@Slf4j
@Component
public class TaskManagementHandler extends AbstractCallbackHandler {
    
    private static final String PAGE_TYPE = "task_management";
    private static final int PAGE_SIZE = 5; // 每页显示5个任务
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        IOciCreateTaskService taskService = SpringUtil.getBean(IOciCreateTaskService.class);
        IOciUserService userService = SpringUtil.getBean(IOciUserService.class);
        
        List<OciCreateTask> taskList = taskService.list();
        
        if (CollectionUtil.isEmpty(taskList)) {
            return buildEditMessage(
                    callbackQuery,
                    "❌ 当前没有正在执行的任务",
                    new InlineKeyboardMarkup(KeyboardBuilder.buildMainMenu())
            );
        }
        
        // 获取选择存储
        long chatId = callbackQuery.getMessage().getChatId();
        PaginationStorage paginationStorage = PaginationStorage.getInstance();
        
        // 重置页码（每次进入任务管理都从第一页开始）
        paginationStorage.resetPage(chatId, PAGE_TYPE);
        
        // 构建带用户信息的任务列表
        Map<String, OciUser> userMap = userService.list().stream()
                .collect(Collectors.toMap(OciUser::getId, u -> u));
        
        return buildTaskManagementMessage(callbackQuery, taskList, userMap, chatId, paginationStorage);
    }
    
    /**
     * 构建任务管理消息
     */
    private BotApiMethod<? extends Serializable> buildTaskManagementMessage(
            CallbackQuery callbackQuery,
            List<OciCreateTask> taskList,
            Map<String, OciUser> userMap,
            long chatId,
            PaginationStorage paginationStorage) {
        
        TaskSelectionStorage selectionStorage = TaskSelectionStorage.getInstance();
        
        int currentPage = paginationStorage.getCurrentPage(chatId, PAGE_TYPE);
        int totalPages = PaginationStorage.calculateTotalPages(taskList.size(), PAGE_SIZE);
        int startIndex = PaginationStorage.getStartIndex(currentPage, PAGE_SIZE);
        int endIndex = PaginationStorage.getEndIndex(currentPage, PAGE_SIZE, taskList.size());
        
        // 获取当前页的任务列表
        List<OciCreateTask> pageTasks = taskList.subList(startIndex, endIndex);
        
        StringBuilder message = new StringBuilder("【任务管理】\n\n");
        message.append(String.format("共 %d 个正在执行的任务，当前第 %d/%d 页：\n\n", 
                taskList.size(), currentPage + 1, totalPages));
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        for (int i = 0; i < pageTasks.size(); i++) {
            OciCreateTask task = pageTasks.get(i);
            OciUser user = userMap.get(task.getUserId());
            
            if (user == null) {
                continue;
            }
            
            Long counts = (Long) TEMP_MAP.get(CommonUtils.CREATE_COUNTS_PREFIX + task.getId());
            boolean isSelected = selectionStorage.isSelected(chatId, task.getId());
            int taskNumber = startIndex + i + 1; // 全局任务编号
            
            message.append(String.format(
                    "%s %d. [%s] [%s] [%s]\n" +
                    "   配置: %s核/%sG/%sG\n" +
                    "   数量: %s台 | 已运行: %s | 尝试: %s次\n\n",
                    isSelected ? "☑️" : "⬜",
                    taskNumber,
                    user.getUsername(),
                    user.getOciRegion(),
                    task.getArchitecture(),
                    task.getOcpus().intValue(),
                    task.getMemory().intValue(),
                    task.getDisk(),
                    task.getCreateNumbers(),
                    CommonUtils.getTimeDifference(task.getCreateTime()),
                    counts == null ? "0" : counts
            ));
            
            // 添加任务按钮（每行2个）
            if (i % 2 == 0) {
                InlineKeyboardRow row = new InlineKeyboardRow();
                row.add(KeyboardBuilder.button(
                        String.format("%s 任务%d", isSelected ? "☑️" : "⬜", taskNumber),
                        "toggle_task:" + task.getId()
                ));
                keyboard.add(row);
            } else {
                keyboard.get(keyboard.size() - 1).add(KeyboardBuilder.button(
                        String.format("%s 任务%d", isSelected ? "☑️" : "⬜", taskNumber),
                        "toggle_task:" + task.getId()
                ));
            }
        }
        
        // 添加分页按钮（如果需要）
        if (totalPages > 1) {
            keyboard.add(KeyboardBuilder.buildPaginationRow(
                    currentPage,
                    totalPages,
                    "task_page_prev",
                    "task_page_next"
            ));
        }
        
        // 添加批量操作按钮
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("✅ 全选", "select_all_tasks"),
                KeyboardBuilder.button("⬜ 取消全选", "deselect_all_tasks")
        ));
        
        keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("🛑 结束选中的任务", "stop_selected_tasks")
        ));
        
        keyboard.add(KeyboardBuilder.buildBackToMainMenuRow());
        keyboard.add(KeyboardBuilder.buildCancelRow());
        
        return buildEditMessage(
                callbackQuery,
                message.toString(),
                new InlineKeyboardMarkup(keyboard)
        );
    }
    
    @Override
    public String getCallbackPattern() {
        return "task_management";
    }
}
