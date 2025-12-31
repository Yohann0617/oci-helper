package com.yohann.ocihelper.telegram.builder;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Telegram Bot 键盘构建器工具类
 * 
 * @author yohann
 */
public class KeyboardBuilder {
    
    /**
     * 构建主菜单键盘
     * 
     * @return 键盘行列表
     */
    public static List<InlineKeyboardRow> buildMainMenu() {
        return Arrays.asList(
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("\uD83D\uDD11 配置列表")
                                .callbackData("config_list")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("\uD83D\uDCC3 任务管理")
                                .callbackData("task_management")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("\uD83D\uDECE 一键测活")
                                .callbackData("check_alive")
                                .build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("\uD83C\uDF10 流量统计")
                                .callbackData("traffic_statistics")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("\uD83D\uDCCA 资源监控")
                                .callbackData("system_metrics")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("\uD83D\uDCCB 日志查询")
                                .callbackData("log_query")
                                .build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("\uD83D\uDEE1\uFE0F 版本信息")
                                .callbackData("version_info")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("\uD83D\uDCE2 通知频道")
                                .url("https://t.me/oci_helper")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("\uD83D\uDD0D 放货查询")
                                .url("https://check.oci-helper.de5.net")
                                .build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("\uD83D\uDCBB 开源地址（帮忙点点star⭐）")
                                .url("https://github.com/Yohann0617/oci-helper")
                                .build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text("❌ 关闭窗口")
                                .callbackData("cancel")
                                .build()
                )
        );
    }
    
    /**
     * 构建返回主菜单按钮行
     * 
     * @return 键盘行
     */
    public static InlineKeyboardRow buildBackToMainMenuRow() {
        return new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("◀️ 返回主菜单")
                        .callbackData("back_to_main")
                        .build()
        );
    }
    
    /**
     * 构建取消按钮行
     * 
     * @return 键盘行
     */
    public static InlineKeyboardRow buildCancelRow() {
        return new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text("❌ 关闭窗口")
                        .callbackData("cancel")
                        .build()
        );
    }
    
    /**
     * 构建带有返回主菜单和取消按钮的键盘行
     * 
     * @param rows 现有的行
     * @return 带有导航按钮的行
     */
    public static List<InlineKeyboardRow> withNavigation(List<InlineKeyboardRow> rows) {
        List<InlineKeyboardRow> result = new ArrayList<>(rows);
        result.add(buildBackToMainMenuRow());
        result.add(buildCancelRow());
        return result;
    }
    
    /**
     * 构建按钮
     * 
     * @param text 按钮文本
     * @param callbackData 回调数据
     * @return 内联键盘按钮
     */
    public static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }
    
    /**
     * 构建 URL 按钮
     * 
     * @param text 按钮文本
     * @param url 链接
     * @return 内联键盘按钮
     */
    public static InlineKeyboardButton urlButton(String text, String url) {
        return InlineKeyboardButton.builder()
                .text(text)
                .url(url)
                .build();
    }
    
    /**
     * 构建分页按钮行
     * 
     * @param currentPage 当前页（从0开始）
     * @param totalPages 总页数
     * @param prevCallback 上一页回调数据
     * @param nextCallback 下一页回调数据
     * @return 分页按钮行
     */
    public static InlineKeyboardRow buildPaginationRow(int currentPage, int totalPages, 
                                                       String prevCallback, String nextCallback) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        
        // 上一页按钮
        if (currentPage > 0) {
            row.add(button("⬅️ 上一页", prevCallback));
        } else {
            row.add(button("　", "noop")); // 占位按钮
        }
        
        // 页码显示
        row.add(button(String.format("%d/%d", currentPage + 1, totalPages), "noop"));
        
        // 下一页按钮
        if (currentPage < totalPages - 1) {
            row.add(button("下一页 ➡️", nextCallback));
        } else {
            row.add(button("　", "noop")); // 占位按钮
        }
        
        return row;
    }
}
