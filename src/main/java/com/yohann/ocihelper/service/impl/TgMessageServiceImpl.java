package com.yohann.ocihelper.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yohann.ocihelper.bean.entity.OciKv;
import com.yohann.ocihelper.enums.SysCfgEnum;
import com.yohann.ocihelper.service.IMessageService;
import com.yohann.ocihelper.service.IOciKvService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * <p>
 * TgMessageServiceImpl
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/8 12:06
 */
@Service
@Slf4j
public class TgMessageServiceImpl implements IMessageService {

    @Resource
    private IOciKvService kvService;

    private static final String TG_URL = "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s";

    @Override
    public void sendMessage(String message) {
        OciKv tgToken = kvService.getOne(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, SysCfgEnum.SYS_TG_BOT_TOKEN.getCode()));
        OciKv tgChatId = kvService.getOne(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, SysCfgEnum.SYS_TG_CHAT_ID.getCode()));

        if (null != tgToken && StrUtil.isNotBlank(tgToken.getValue()) &&
                null != tgChatId && StrUtil.isNotBlank(tgChatId.getValue())) {
            doSend(message, tgToken.getValue(), tgChatId.getValue());
        }
    }

    private void doSend(String message, String botToken, String chatId) {
        try {
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.toString());
            String urlString = String.format(TG_URL, botToken, chatId, encodedMessage);
            HttpResponse response = HttpUtil.createGet(urlString).execute();

            if (response.getStatus() == 200) {
                log.info("telegram message send successfully!");
            } else {
                log.info("failed to send telegram message, response code: [{}]", response.getStatus());
            }
        } catch (Exception e) {
            log.error("error while sending telegram message: ", e);
//            throw new RuntimeException(e);
        }
    }
}
