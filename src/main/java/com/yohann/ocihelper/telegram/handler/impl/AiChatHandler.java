package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import com.yohann.ocihelper.telegram.storage.ChatSessionStorage;
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
 * AI Chat Handler
 * Handles AI chat menu and settings
 * 
 * @author yohann
 */
@Slf4j
@Component
public class AiChatHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        ChatSessionStorage storage = ChatSessionStorage.getInstance();
        
        // Get current settings
        String currentModel = storage.getModel(chatId);
        boolean internetEnabled = storage.isInternetEnabled(chatId);
        int historyCount = storage.getHistory(chatId).size();
        
        String text = String.format(
            "🤖 *AI 聊天助手*\n\n" +
            "📌 当前设置：\n" +
            "• 模型: %s\n" +
            "• 联网搜索: %s\n" +
            "• 会话消息数: %d\n\n" +
            "💡 使用说明：\n" +
            "直接在聊天中输入消息即可与 AI 对话\n" +
            "AI 会记住最近 10 条对话内容\n\n" +
            "⚙️ 请选择功能：",
            getModelDisplayName(currentModel),
            internetEnabled ? "✅ 已开启" : "❌ 已关闭",
            historyCount
        );
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        // Model selection row
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("🔄 切换模型", "ai_select_model")
        ));
        
        // Internet search toggle row
        String internetButtonText = internetEnabled ? "🌐 关闭联网搜索" : "🌐 开启联网搜索";
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button(internetButtonText, "ai_toggle_internet")
        ));
        
        // Clear history row
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("🗑️ 清空会话历史", "ai_clear_history")
        ));
        
        // Navigation
        keyboard.add(KeyboardBuilder.buildBackToMainMenuRow());
        keyboard.add(KeyboardBuilder.buildCancelRow());
        
        return buildEditMessage(
            callbackQuery,
            text,
            new InlineKeyboardMarkup(keyboard)
        );
    }
    
    @Override
    public String getCallbackPattern() {
        return "ai_chat";
    }
    
    /**
     * Get model display name
     */
    private String getModelDisplayName(String model) {
        if (model.contains("DeepSeek-R1")) {
            return "DeepSeek-R1 (推理模型)";
        } else if (model.contains("DeepSeek-V3")) {
            return "DeepSeek-V3 (通用模型)";
        } else if (model.contains("Qwen")) {
            return "Qwen (通义千问)";
        }
        return model;
    }
}

/**
 * Model Selection Handler
 */
@Slf4j
@Component
class AiModelSelectionHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String text = "🤖 *选择 AI 模型*\n\n" +
                     "请选择要使用的 AI 模型：\n\n" +
                     "💡 不同模型有不同特点：\n" +
                     "• DeepSeek-R1: 推理能力强，适合复杂问题\n" +
                     "• DeepSeek-V3: 通用能力强，响应速度快\n" +
                     "• Qwen: 中文优化好，对话自然";
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("🧠 DeepSeek-R1", "ai_set_model_deepseek_r1")
        ));
        
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("⚡ DeepSeek-V3", "ai_set_model_deepseek_v3")
        ));
        
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("🌟 Qwen-2.5", "ai_set_model_qwen")
        ));
        
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("◀️ 返回", "ai_chat")
        ));
        
        keyboard.add(KeyboardBuilder.buildCancelRow());
        
        return buildEditMessage(
            callbackQuery,
            text,
            new InlineKeyboardMarkup(keyboard)
        );
    }
    
    @Override
    public String getCallbackPattern() {
        return "ai_select_model";
    }
}

/**
 * Set Model Handler
 */
@Slf4j
@Component
class AiSetModelHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        String callbackData = callbackQuery.getData();
        
        ChatSessionStorage storage = ChatSessionStorage.getInstance();
        String modelName;
        String displayName;
        
        if (callbackData.contains("deepseek_r1")) {
            modelName = "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B";
            displayName = "DeepSeek-R1";
        } else if (callbackData.contains("deepseek_v3")) {
            modelName = "deepseek-ai/DeepSeek-V3";
            displayName = "DeepSeek-V3";
        } else if (callbackData.contains("qwen")) {
            modelName = "Qwen/Qwen2.5-7B-Instruct";
            displayName = "Qwen-2.5";
        } else {
            modelName = "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B";
            displayName = "DeepSeek-R1";
        }
        
        storage.setModel(chatId, modelName);
        log.info("AI model changed: chatId={}, model={}", chatId, modelName);
        
        String text = String.format("✅ 已切换到 *%s* 模型\n\n可以开始对话了！", displayName);
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("◀️ 返回 AI 设置", "ai_chat")
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());
        
        return buildEditMessage(
            callbackQuery,
            text,
            new InlineKeyboardMarkup(keyboard)
        );
    }
    
    @Override
    public String getCallbackPattern() {
        return "ai_set_model";
    }
    
    @Override
    public boolean canHandle(String callbackData) {
        return callbackData != null && callbackData.startsWith("ai_set_model_");
    }
}

/**
 * Toggle Internet Search Handler
 */
@Slf4j
@Component
class AiToggleInternetHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        ChatSessionStorage storage = ChatSessionStorage.getInstance();
        
        boolean currentStatus = storage.isInternetEnabled(chatId);
        storage.setInternetEnabled(chatId, !currentStatus);
        
        String statusText = !currentStatus ? "已开启" : "已关闭";
        log.info("AI internet search toggled: chatId={}, enabled={}", chatId, !currentStatus);
        
        String text = String.format("✅ 联网搜索%s\n\n%s", 
            statusText,
            !currentStatus ? "AI 将能够搜索最新信息来回答问题" : "AI 将仅使用训练数据回答问题"
        );
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("◀️ 返回 AI 设置", "ai_chat")
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());
        
        return buildEditMessage(
            callbackQuery,
            text,
            new InlineKeyboardMarkup(keyboard)
        );
    }
    
    @Override
    public String getCallbackPattern() {
        return "ai_toggle_internet";
    }
}

/**
 * Clear History Handler
 */
@Slf4j
@Component
class AiClearHistoryHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        ChatSessionStorage storage = ChatSessionStorage.getInstance();
        
        storage.clearHistory(chatId);
        log.info("AI chat history cleared: chatId={}", chatId);
        
        String text = "✅ 会话历史已清空\n\n可以开始新的对话了！";
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("◀️ 返回 AI 设置", "ai_chat")
        ));
        keyboard.add(KeyboardBuilder.buildCancelRow());
        
        return buildEditMessage(
            callbackQuery,
            text,
            new InlineKeyboardMarkup(keyboard)
        );
    }
    
    @Override
    public String getCallbackPattern() {
        return "ai_clear_history";
    }
}
