package com.yohann.ocihelper.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yohann.ocihelper.bean.entity.OciKv;
import com.yohann.ocihelper.enums.SysCfgEnum;
import com.yohann.ocihelper.service.IMessageService;
import com.yohann.ocihelper.service.IOciKvService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * DingMessageServiceImpl
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/8 12:06
 */
@Service
@Slf4j
public class DingMessageServiceImpl implements IMessageService {

    @Resource
    private IOciKvService kvService;

    private static final String DING_URL = "https://oapi.dingtalk.com/robot/send?access_token=%s";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void sendMessage(String message) {
        OciKv dingToken = kvService.getOne(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, SysCfgEnum.SYS_DING_BOT_TOKEN.getCode()));
        OciKv dingSecret = kvService.getOne(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, SysCfgEnum.SYS_DING_BOT_SECRET.getCode()));

        if (null != dingToken && StrUtil.isNotBlank(dingToken.getValue()) &&
                null != dingSecret && StrUtil.isNotBlank(dingSecret.getValue())) {
            try {
                sendDingTalkMessage(String.format(DING_URL, dingToken.getValue()), dingSecret.getValue(), message);
            } catch (Exception e) {
                log.info("Failed to send dingding message, error: {}", e.getLocalizedMessage());
            }
        }
    }

    public static void sendDingTalkMessage(String webhook, String secret, String message) throws Exception {
        String url = webhook;
        if (secret != null && !secret.isEmpty()) {
            long timestamp = System.currentTimeMillis();
            String sign = generateSign(timestamp, secret);
            url += "&timestamp=" + timestamp + "&sign=" + URLEncoder.encode(sign, "UTF-8");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("msgtype", "text");
        Map<String, String> text = new HashMap<>();
        text.put("content", message);
        payload.put("text", text);

        sendPostRequest(url, OBJECT_MAPPER.writeValueAsString(payload));
    }

    private static String generateSign(long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return new String(Base64.encodeBase64(signData));
    }

    private static void sendPostRequest(String url, String jsonPayload) throws Exception {
        URL connectionUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) connectionUrl.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            log.info("dingding message sent successfully!");
        } else {
            log.info("Failed to send dingding message, HTTP response code: {}", responseCode);
        }
    }
}
