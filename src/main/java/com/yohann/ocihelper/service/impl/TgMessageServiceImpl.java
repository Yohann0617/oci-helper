package com.yohann.ocihelper.service.impl;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.yohann.ocihelper.service.IMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

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

    @Value("${tg-cfg.chat-id}")
    private String chatId;
    @Value("${tg-cfg.token}")
    private String botToken;

    private static final String TG_URL = "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s";

    @Override
    public void sendMessage(String message) {
        doSend(message);
    }

    private void doSend(String message) {
        try {
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.toString());
            String urlString = String.format(TG_URL, botToken, chatId, encodedMessage);
            HttpResponse response = HttpUtil.createGet(urlString).execute();

            if (response.getStatus() == 200) {
                log.info("telegram message sent successfully!");
            } else {
                log.info("failed to send telegram message, response code: [{}]", response.getStatus());
            }
        } catch (Exception e) {
//            log.error("error while sending telegram message: ", e);
            throw new RuntimeException(e);
        }
    }
}
