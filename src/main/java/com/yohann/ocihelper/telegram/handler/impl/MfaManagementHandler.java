package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.yohann.ocihelper.bean.params.sys.UpdateSysCfgParams;
import com.yohann.ocihelper.bean.response.sys.GetSysCfgRsp;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import com.yohann.ocihelper.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
 * MFA Management Handler
 * Handles MFA enable/disable and code generation
 * 
 * @author yohann
 */
@Slf4j
@Component
public class MfaManagementHandler extends AbstractCallbackHandler {
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        ISysService sysService = SpringUtil.getBean(ISysService.class);
        
        try {
            // Get system configuration
            GetSysCfgRsp sysCfg = sysService.getSysCfg();
            
            boolean mfaEnabled = sysCfg.getEnableMfa() != null && sysCfg.getEnableMfa();
            boolean hasSecret = StringUtils.isNotBlank(sysCfg.getMfaSecret());
            
            String text;
            List<InlineKeyboardRow> keyboard = new ArrayList<>();
            
            if (mfaEnabled && hasSecret) {
                // MFA is enabled and has secret - show code
                int mfaCode = CommonUtils.generateMfaCode(sysCfg.getMfaSecret());
                String formattedCode = String.format("%06d", mfaCode);
                
                text = String.format(
                    "🔐 *MFA 管理*\n\n" +
                    "📌 当前状态：✅ 已启用\n\n" +
                    "🔑 当前验证码：\n" +
                    "`%s`\n\n" +
                    "💡 使用说明：\n" +
                    "• 验证码每 30 秒更新一次\n" +
                    "• 点击验证码可复制\n" +
                    "• 用于需要 MFA 认证的场景\n\n" +
                    "⚠️ 注意：\n" +
                    "关闭 MFA 不会删除密钥，只是禁用功能。\n" +
                    "请妥善保管验证码，不要泄露。\n\n" +
                    "⚙️ 请选择功能：",
                    formattedCode
                );
                
                keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("🔄 刷新验证码", "mfa_refresh")
                ));
                
                keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("🔴 关闭 MFA", "mfa_disable_confirm")
                ));
                
            } else if (!mfaEnabled && hasSecret) {
                // Has secret but MFA is disabled
                text = "🔐 *MFA 管理*\n\n" +
                       "📌 当前状态：⚪ 已禁用\n\n" +
                       "📝 说明：\n" +
                       "MFA 密钥已配置，但功能处于禁用状态。\n" +
                       "点击下方按钮即可重新启用。\n\n" +
                       "⚠️ 注意：\n" +
                       "如果需要重新生成密钥和二维码，\n" +
                       "请先删除当前密钥，然后在 Web 界面重新配置。\n\n" +
                       "⚙️ 请选择功能：";
                
                keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("✅ 启用 MFA", "mfa_enable"),
                    KeyboardBuilder.button("🗑️ 删除密钥", "mfa_delete_secret")
                ));
                
            } else {
                // No secret configured
                text = "🔐 *MFA 管理*\n\n" +
                       "📋 当前状态：⚪ 未配置\n\n" +
                       "💡 什么是 MFA？\n" +
                       "MFA (Multi-Factor Authentication) 是一种安全认证机制，\n" +
                       "通过生成一次性验证码来增强账户安全性。\n\n" +
                       "📱 使用场景：\n" +
                       "• OCI 账户登录\n" +
                       "• 需要双因素认证的服务\n" +
                       "• 敏感操作确认\n\n" +
                       "⚙️ 启用方式：\n" +
                       "点击下方按钮即可启用 MFA。\n" +
                       "系统会自动生成密钥和二维码。\n\n" +
                       "💡 提示：\n" +
                       "• 启用后会收到二维码图片\n" +
                       "• 使用身份验证器应用扫描二维码\n" +
                       "• 配置完成后即可使用验证码";
                
                keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("✅ 启用 MFA", "mfa_enable")
                ));
            }
            
            keyboard.add(KeyboardBuilder.buildBackToMainMenuRow());
            keyboard.add(KeyboardBuilder.buildCancelRow());
            
            return buildEditMessage(
                callbackQuery,
                text,
                new InlineKeyboardMarkup(keyboard)
            );
            
        } catch (Exception e) {
            log.error("Failed to get MFA management info", e);
            return buildErrorMessage(callbackQuery, e.getMessage());
        }
    }
    
    @Override
    public String getCallbackPattern() {
        return "mfa_management";
    }
    
    /**
     * Build error message
     */
    private BotApiMethod<? extends Serializable> buildErrorMessage(CallbackQuery callbackQuery, String errorMsg) {
        String text = String.format(
            "❌ *获取 MFA 信息失败*\n\n" +
            "错误信息：%s\n\n" +
            "请稍后重试或联系管理员。",
            errorMsg
        );
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(KeyboardBuilder.buildBackToMainMenuRow());
        keyboard.add(KeyboardBuilder.buildCancelRow());
        
        return buildEditMessage(
            callbackQuery,
            text,
            new InlineKeyboardMarkup(keyboard)
        );
    }
}

/**
 * MFA Refresh Handler
 * Refreshes the MFA code display
 */
@Component
class MfaRefreshHandler extends AbstractCallbackHandler {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MfaRefreshHandler.class);
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        // Just redirect back to MFA management to refresh
        return new MfaManagementHandler().handle(callbackQuery, telegramClient);
    }
    
    @Override
    public String getCallbackPattern() {
        return "mfa_refresh";
    }
}

/**
 * MFA Enable Handler
 * Enables MFA using updateSysCfg API and sends QR code
 */
@Component
class MfaEnableHandler extends AbstractCallbackHandler {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MfaEnableHandler.class);
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        ISysService sysService = SpringUtil.getBean(ISysService.class);
        long chatId = callbackQuery.getMessage().getChatId();
        
        try {
            // Show processing message
            telegramClient.execute(buildEditMessage(
                callbackQuery,
                "⏳ 正在启用 MFA...\n\n正在生成密钥和二维码，请稍候。",
                null
            ));
            
            // Get current config first
            GetSysCfgRsp sysCfg = sysService.getSysCfg();
            
            // Enable MFA (this will auto-generate secret and QR code)
            UpdateSysCfgParams params = new UpdateSysCfgParams();
            params.setEnableMfa(true);
            params.setTgBotToken(sysCfg.getTgBotToken());
            params.setTgChatId(sysCfg.getTgChatId());
            params.setDingToken(sysCfg.getDingToken());
            params.setDingSecret(sysCfg.getDingSecret());
            params.setEnableDailyBroadcast(sysCfg.getEnableDailyBroadcast());
            params.setDailyBroadcastCron(sysCfg.getDailyBroadcastCron());
            params.setEnableVersionInform(sysCfg.getEnableVersionInform());
            params.setGjAiApi(sysCfg.getGjAiApi());
            params.setBootBroadcastToken(sysCfg.getBootBroadcastToken());
            
            sysService.updateSysCfg(params);
            
            log.info("MFA enabled for chatId: {}", chatId);
            
            // Refresh config to get the newly generated secret
            sysCfg = sysService.getSysCfg();
            String formattedCode = "N/A";
            
            if (StringUtils.isNotBlank(sysCfg.getMfaSecret())) {
                int mfaCode = CommonUtils.generateMfaCode(sysCfg.getMfaSecret());
                formattedCode = String.format("%06d", mfaCode);
            }
            
            // Send QR code image
            java.io.File qrFile = new java.io.File(CommonUtils.MFA_QR_PNG_PATH);
            if (qrFile.exists()) {
                org.telegram.telegrambots.meta.api.methods.send.SendPhoto sendPhoto = 
                    org.telegram.telegrambots.meta.api.methods.send.SendPhoto.builder()
                        .chatId(chatId)
                        .photo(new org.telegram.telegrambots.meta.api.objects.InputFile(qrFile))
                        .caption(
                            "📱 *MFA 二维码*\n\n" +
                            "请使用身份验证器应用（如 Google Authenticator、Microsoft Authenticator 等）\n" +
                            "扫描此二维码来添加账户。\n\n" +
                            "⚠️ 注意：\n" +
                            "• 请妥善保管此二维码\n" +
                            "• 扫描后即可删除此图片\n" +
                            "• 如需重新生成，请先删除密钥"
                        )
                        .parseMode("Markdown")
                        .build();
                
                try {
                    telegramClient.execute(sendPhoto);
                } catch (Exception e) {
                    log.error("Failed to send QR code image", e);
                }
            }
            
            String text = String.format(
                "✅ *MFA 已启用*\n\n" +
                "🔑 当前验证码：\n" +
                "`%s`\n\n" +
                "📱 配置步骤：\n" +
                "1️⃣ 查看上方发送的二维码图片\n" +
                "2️⃣ 使用身份验证器应用扫描\n" +
                "3️⃣ 应用中会显示 6 位数字验证码\n" +
                "4️⃣ 使用该验证码进行 MFA 认证\n\n" +
                "💡 提示：\n" +
                "• 验证码每 30 秒更新一次\n" +
                "• 随时可在此查看当前验证码\n" +
                "• 二维码只在首次配置时需要",
                formattedCode
            );
            
            List<InlineKeyboardRow> keyboard = new ArrayList<>();
            keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回", "mfa_management")
            ));
            keyboard.add(KeyboardBuilder.buildCancelRow());
            
            return buildEditMessage(
                callbackQuery,
                text,
                new InlineKeyboardMarkup(keyboard)
            );
            
        } catch (Exception e) {
            log.error("Failed to enable MFA", e);
            
            String text = "❌ *启用 MFA 失败*\n\n" +
                         "错误信息：" + e.getMessage();
            
            List<InlineKeyboardRow> keyboard = new ArrayList<>();
            keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回", "mfa_management")
            ));
            keyboard.add(KeyboardBuilder.buildCancelRow());
            
            return buildEditMessage(
                callbackQuery,
                text,
                new InlineKeyboardMarkup(keyboard)
            );
        }
    }
    
    @Override
    public String getCallbackPattern() {
        return "mfa_enable";
    }
}

/**
 * MFA Disable Confirm Handler
 * Confirms before disabling MFA
 */
@Component
class MfaDisableConfirmHandler extends AbstractCallbackHandler {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MfaDisableConfirmHandler.class);
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String text = "⚠️ *确认关闭 MFA*\n\n" +
                     "您确定要关闭 MFA 功能吗？\n\n" +
                     "关闭后将：\n" +
                     "• 无法生成验证码\n" +
                     "• MFA 密钥仍会保留\n" +
                     "• 可以随时重新启用\n\n" +
                     "💡 提示：\n" +
                     "如果只是暂时不使用，建议保持启用状态。";
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("✅ 确认关闭", "mfa_disable"),
            KeyboardBuilder.button("❌ 取消", "mfa_management")
        ));
        
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("◀️ 返回", "mfa_management")
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
        return "mfa_disable_confirm";
    }
}

/**
 * MFA Disable Handler
 * Disables MFA using updateSysCfg API
 */
@Component
class MfaDisableHandler extends AbstractCallbackHandler {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MfaDisableHandler.class);
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        ISysService sysService = SpringUtil.getBean(ISysService.class);
        
        try {
            // Get current config first
            GetSysCfgRsp sysCfg = sysService.getSysCfg();
            
            // Disable MFA
            UpdateSysCfgParams params = new UpdateSysCfgParams();
            params.setEnableMfa(false);
            params.setTgBotToken(sysCfg.getTgBotToken());
            params.setTgChatId(sysCfg.getTgChatId());
            params.setDingToken(sysCfg.getDingToken());
            params.setDingSecret(sysCfg.getDingSecret());
            params.setEnableDailyBroadcast(sysCfg.getEnableDailyBroadcast());
            params.setDailyBroadcastCron(sysCfg.getDailyBroadcastCron());
            params.setEnableVersionInform(sysCfg.getEnableVersionInform());
            params.setGjAiApi(sysCfg.getGjAiApi());
            params.setBootBroadcastToken(sysCfg.getBootBroadcastToken());
            
            sysService.updateSysCfg(params);
            
            log.info("MFA disabled for chatId: {}", callbackQuery.getMessage().getChatId());
            
            String text = "✅ *MFA 已关闭*\n\n" +
                         "MFA 功能已禁用。\n\n" +
                         "💡 提示：\n" +
                         "• MFA 密钥仍会保留\n" +
                         "• 需要时可以重新启用";
            
            List<InlineKeyboardRow> keyboard = new ArrayList<>();
            keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回", "mfa_management")
            ));
            keyboard.add(KeyboardBuilder.buildCancelRow());
            
            return buildEditMessage(
                callbackQuery,
                text,
                new InlineKeyboardMarkup(keyboard)
            );
            
        } catch (Exception e) {
            log.error("Failed to disable MFA", e);
            
            String text = "❌ *关闭 MFA 失败*\n\n" +
                         "错误信息：" + e.getMessage();
            
            List<InlineKeyboardRow> keyboard = new ArrayList<>();
            keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回", "mfa_management")
            ));
            keyboard.add(KeyboardBuilder.buildCancelRow());
            
            return buildEditMessage(
                callbackQuery,
                text,
                new InlineKeyboardMarkup(keyboard)
            );
        }
    }
    
    @Override
    public String getCallbackPattern() {
        return "mfa_disable";
    }
}

/**
 * MFA Delete Secret Handler
 * Deletes MFA secret key (will be regenerated when re-enabled via Web)
 */
@Component
class MfaDeleteSecretHandler extends AbstractCallbackHandler {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MfaDeleteSecretHandler.class);
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String text = "⚠️ *确认删除 MFA 密钥*\n\n" +
                     "您确定要删除当前的 MFA 密钥吗？\n\n" +
                     "删除后将：\n" +
                     "• 当前密钥不可恢复\n" +
                     "• 需要通过 Web 界面重新生成\n" +
                     "• 需要重新扫描二维码配置\n\n" +
                     "💡 提示：\n" +
                     "如果只是不想使用，建议使用「关闭 MFA」功能。";
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("✅ 确认删除", "mfa_delete_secret_confirm"),
            KeyboardBuilder.button("❌ 取消", "mfa_management")
        ));
        
        keyboard.add(new InlineKeyboardRow(
            KeyboardBuilder.button("◀️ 返回", "mfa_management")
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
        return "mfa_delete_secret";
    }
}

/**
 * MFA Delete Secret Confirm Handler
 * Actually deletes the MFA secret
 */
@Component
class MfaDeleteSecretConfirmHandler extends AbstractCallbackHandler {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MfaDeleteSecretConfirmHandler.class);
    
    @Override
    public BotApiMethod<? extends Serializable> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        ISysService sysService = SpringUtil.getBean(ISysService.class);
        
        try {
            // Get current config
            GetSysCfgRsp sysCfg = sysService.getSysCfg();
            
            // Disable MFA (this will delete the secret)
            UpdateSysCfgParams params = new UpdateSysCfgParams();
            params.setEnableMfa(false);
            params.setTgBotToken(sysCfg.getTgBotToken());
            params.setTgChatId(sysCfg.getTgChatId());
            params.setDingToken(sysCfg.getDingToken());
            params.setDingSecret(sysCfg.getDingSecret());
            params.setEnableDailyBroadcast(sysCfg.getEnableDailyBroadcast());
            params.setDailyBroadcastCron(sysCfg.getDailyBroadcastCron());
            params.setEnableVersionInform(sysCfg.getEnableVersionInform());
            params.setGjAiApi(sysCfg.getGjAiApi());
            params.setBootBroadcastToken(sysCfg.getBootBroadcastToken());
            
            sysService.updateSysCfg(params);
            
            log.info("MFA secret deleted for chatId: {}", callbackQuery.getMessage().getChatId());
            
            String text = "✅ *MFA 密钥已删除*\n\n" +
                         "当前密钥和二维码已删除。\n\n" +
                         "🔗 重新配置步骤：\n" +
                         "1️⃣ 访问系统 Web 管理界面\n" +
                         "2️⃣ 进入「系统配置」页面\n" +
                         "3️⃣ 启用 MFA，系统会生成新密钥\n" +
                         "4️⃣ 扫描新的二维码配置\n\n" +
                         "💡 提示：\n" +
                         "配置完成后即可在此处查看验证码。";
            
            List<InlineKeyboardRow> keyboard = new ArrayList<>();
            keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回", "mfa_management")
            ));
            keyboard.add(KeyboardBuilder.buildCancelRow());
            
            return buildEditMessage(
                callbackQuery,
                text,
                new InlineKeyboardMarkup(keyboard)
            );
            
        } catch (Exception e) {
            log.error("Failed to delete MFA secret", e);
            
            String text = "❌ *删除 MFA 密钥失败*\n\n" +
                         "错误信息：" + e.getMessage();
            
            List<InlineKeyboardRow> keyboard = new ArrayList<>();
            keyboard.add(new InlineKeyboardRow(
                KeyboardBuilder.button("◀️ 返回", "mfa_management")
            ));
            keyboard.add(KeyboardBuilder.buildCancelRow());
            
            return buildEditMessage(
                callbackQuery,
                text,
                new InlineKeyboardMarkup(keyboard)
            );
        }
    }
    
    @Override
    public String getCallbackPattern() {
        return "mfa_delete_secret_confirm";
    }
}
