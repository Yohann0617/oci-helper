package com.yohann.ocihelper.service.impl;

import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yohann.ocihelper.bean.entity.OciKv;
import com.yohann.ocihelper.enums.SysCfgEnum;
import com.yohann.ocihelper.service.IMessageService;
import com.yohann.ocihelper.service.IOciKvService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
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
            
            // 获取全局代理配置
            OciKv proxyKv = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                    .eq(OciKv::getCode, SysCfgEnum.SYS_PROXY.getCode()));
            String proxyUrl = proxyKv != null ? proxyKv.getValue() : null;
            
            HttpRequest request = HttpUtil.createGet(urlString);
            
            // 如果配置了代理，则应用代理
            if (StrUtil.isNotBlank(proxyUrl)) {
                Proxy proxy = parseProxy(proxyUrl);
                if (proxy != null) {
                    request.setProxy(proxy);
                    log.debug("Telegram 消息发送使用代理：{}", proxyUrl);
                }
            }
            
            HttpResponse response = request.execute();

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
    
    /**
     * 解析代理地址
     */
    private Proxy parseProxy(String proxyUrl) {
        try {
            URI uri = new URI(proxyUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            String host = uri.getHost();
            int port = uri.getPort();
            
            if (StrUtil.isBlank(host) || port <= 0) {
                log.warn("Telegram 代理地址格式不正确：{}", proxyUrl);
                return null;
            }
            
            Proxy.Type type;
            if ("http".equals(scheme) || "https".equals(scheme)) {
                type = Proxy.Type.HTTP;
            } else if (scheme.startsWith("socks")) {
                type = Proxy.Type.SOCKS;
            } else {
                log.warn("不支持的 Telegram 代理协议 [{}]", scheme);
                return null;
            }
            
            return new Proxy(type, new InetSocketAddress(host, port));
        } catch (Exception e) {
            log.warn("解析 Telegram 代理地址失败：{}", e.getMessage());
            return null;
        }
    }
}
