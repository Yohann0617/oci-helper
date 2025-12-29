package com.yohann.ocihelper.telegram.handler.impl;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yohann.ocihelper.bean.entity.OciKv;
import com.yohann.ocihelper.enums.SysCfgEnum;
import com.yohann.ocihelper.enums.SysCfgTypeEnum;
import com.yohann.ocihelper.service.IOciKvService;
import com.yohann.ocihelper.telegram.builder.KeyboardBuilder;
import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import com.yohann.ocihelper.utils.CommonUtils;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.toIntExact;

/**
 * 版本信息基础处理器（共享逻辑）
 * 
 * @author yohann
 */
public abstract class VersionInfoBaseHandler extends AbstractCallbackHandler {
    
    protected BotApiMethod<? extends Serializable> getVersionInfo(long chatId, long messageId, TelegramClient telegramClient) {
        String content = "【版本信息】\n\n当前版本：%s\n最新版本：%s\n";
        IOciKvService kvService = SpringUtil.getBean(IOciKvService.class);
        String latest = CommonUtils.getLatestVersion();
        String now = kvService.getObj(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_INFO_VERSION.getCode())
                .eq(OciKv::getType, SysCfgTypeEnum.SYS_INFO.getCode())
                .select(OciKv::getValue), String::valueOf);
        String common = String.format(content, now, latest);
        
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        
        if (!now.equals(latest)) {
            common += String.format("一键脚本：%s\n更新内容：\n%s",
                    "bash <(wget -qO- https://github.com/Yohann0617/oci-helper/releases/latest/download/sh_oci-helper_install.sh)",
                    CommonUtils.getLatestVersionBody());
            
            keyboard.add(new InlineKeyboardRow(
                    KeyboardBuilder.button("\uD83D\uDD04 点击更新至最新版本", "update_sys_version")
            ));
            keyboard.add(KeyboardBuilder.buildCancelRow());
        } else {
            common += "当前已是最新版本，无需更新~";
            keyboard = KeyboardBuilder.buildMainMenu();
        }
        
        return EditMessageText.builder()
                .chatId(chatId)
                .messageId(toIntExact(messageId))
                .text(common)
                .replyMarkup(new InlineKeyboardMarkup(keyboard))
                .build();
    }
}
