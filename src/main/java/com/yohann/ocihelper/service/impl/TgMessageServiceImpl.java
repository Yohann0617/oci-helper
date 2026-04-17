package com.yohann.ocihelper.service.impl;

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
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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

    private static final String TG_HOST = "api.telegram.org";
    private static final int    TG_PORT = 443;
    private static final String TG_URL  = "https://api.telegram.org/bot%s/sendMessage?chat_id=%s&text=%s";

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
        // 获取全局代理配置
        OciKv proxyKv = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_PROXY.getCode()));
        String proxyUrl = proxyKv != null ? proxyKv.getValue() : null;

        try {
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
            String urlString = String.format(TG_URL, botToken, chatId, encodedMessage);

            HttpRequest request = HttpUtil.createGet(urlString);

            if (StrUtil.isNotBlank(proxyUrl)) {
                applyProxy(request, proxyUrl);
            }

            HttpResponse response = request.execute();
            if (response.getStatus() == 200) {
                log.info("telegram message send successfully!");
            } else {
                log.info("failed to send telegram message, response code: [{}]", response.getStatus());
            }
        } catch (Exception e) {
            log.error("error while sending telegram message: ", e);
        }
    }

    /**
     * 将代理配置应用到 HttpRequest。
     * <ul>
     *   <li>HTTP 代理带认证：通过 {@code Proxy-Authorization} 请求头传递，线程安全</li>
     *   <li>SOCKS5 代理带认证：手动完成 RFC 1928/1929 握手建立隔道 Socket，再叠加 TLS，
     *       完全线程安全，无需任何第三方库</li>
     * </ul>
     */
    private void applyProxy(HttpRequest request, String proxyUrl) {
        try {
            URI uri = new URI(proxyUrl.trim());
            String scheme   = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            String host     = uri.getHost();
            int    port     = uri.getPort();
            String userInfo = uri.getUserInfo();

            if (StrUtil.isBlank(host) || port <= 0) {
                log.warn("Telegram 代理地址格式不正确，已忽略：{}", proxyUrl);
                return;
            }

            if ("http".equals(scheme) || "https".equals(scheme)) {
                // HTTP 代理：通过 Proxy-Authorization 请求头传递认证，线程安全
                request.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
                if (StrUtil.isNotBlank(userInfo)) {
                    String encoded = Base64.getEncoder()
                            .encodeToString(userInfo.getBytes(StandardCharsets.UTF_8));
                    request.header("Proxy-Authorization", "Basic " + encoded);
                    log.debug("Telegram HTTP 代理使用认证头，用户：{}",
                            userInfo.contains(":") ? userInfo.substring(0, userInfo.indexOf(':')) : userInfo);
                }
                log.debug("Telegram 消息发送使用 HTTP 代理：{}", proxyUrl);

            } else if (scheme.startsWith("socks")) {
                if (StrUtil.isNotBlank(userInfo)) {
                    // SOCKS5 带认证：手动握手建立隔道，线程安全
                    int    idx  = userInfo.indexOf(':');
                    String user = idx >= 0 ? userInfo.substring(0, idx) : userInfo;
                    String pwd  = idx >= 0 ? userInfo.substring(idx + 1) : "";
                    applyProxyViaSocks5Tunnel(request, host, port, user, pwd);
                    log.debug("Telegram 消息发送使用 SOCKS5 带认证代理，用户：{}", user);
                } else {
                    // SOCKS5 无认证：直接使用标准 Proxy
                    request.setProxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port)));
                    log.debug("Telegram 消息发送使用 SOCKS5 无认证代理：{}", proxyUrl);
                }
            } else {
                log.warn("不支持的 Telegram 代理协议 [{}]，已忽略：{}", scheme, proxyUrl);
            }
        } catch (Exception e) {
            log.warn("应用 Telegram 代理失败，已忽略 [{}]：{}", proxyUrl, e.getMessage());
        }
    }

    /**
     * 通过手动完成 SOCKS5 握手建立隔道 Socket，再叠加 TLS，
     * 继而直接在该 Socket 上发送 HTTP 报文。
     * 绕过 Hutool 和 JDK 内置的 SOCKS5 认证机制，完全线程安全。
     */
    private void applyProxyViaSocks5Tunnel(HttpRequest request,
                                           String proxyHost, int proxyPort,
                                           String username, String password) throws IOException {
        // 手动完成 SOCKS5 握手，得到经过认证的原始 TCP 隔道
        Socket tunnel = socks5Handshake(proxyHost, proxyPort, TG_HOST, TG_PORT, username, password);
        try {
            // 在隔道上叠加 TLS（Telegram 使用 HTTPS/443）
            SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            // 注： createSocket(tunnel, ..., true) 中第四个参数 autoClose=true，
            //      意味着 sslSocket.close() 时会自动关闭底层 tunnel，
            //      因此 sslSocket 关闭后 tunnel 无需再单独处理。
            SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(tunnel, TG_HOST, TG_PORT, true);
            sslSocket.startHandshake();
            // 在已建立的 SSL Socket 上直接发送 HTTP/1.1 报文（内部会关闭 sslSocket）
            sendHttpOverSocket(sslSocket, request.getUrl().toString());
        } catch (IOException e) {
            // createSocket 或 startHandshake 失败时 sslSocket 可能尚未建立，
            // tunnel 没有被 autoClose 覆盖，需手动关闭
            tunnel.close();
            throw e;
        }
    }

    /**
     * 在已建立的 SSL Socket 上直接发送 HTTP/1.1 GET 请求并读取响应状态。
     * 使用 try-with-resources 确保任何路径下 sslSocket 都会被关闭，无内存泄漏风险。
     * 仅用于 SOCKS5 带认证场景。
     */
    private void sendHttpOverSocket(SSLSocket sslSocket, String fullUrl) throws IOException {
        try (sslSocket) {
            String path = fullUrl.replaceFirst("https://[^/]+", "");
            if (StrUtil.isBlank(path)) {
                path = "/";
            }

            String rawRequest = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + TG_HOST + "\r\n"
                    + "Connection: close\r\n"
                    + "\r\n";

            OutputStream out = sslSocket.getOutputStream();
            InputStream  in  = sslSocket.getInputStream();

            out.write(rawRequest.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // 读取响应状态行
            StringBuilder statusLine = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {
                statusLine.append((char) b);
                if (statusLine.toString().endsWith("\r\n")) {
                    break;
                }
            }
            String status = statusLine.toString().trim();
            if (status.contains(" ")) {
                String code = status.split(" ")[1];
                if ("200".equals(code)) {
                    log.info("telegram message send successfully! (via SOCKS5 tunnel)");
                } else {
                    log.info("failed to send telegram message via SOCKS5 tunnel, code: [{}]", code);
                }
            }
        }
    }

    /**
     * 手动完成 SOCKS5 RFC 1928 版本协商 + RFC 1929 用户密码子协商握手，
     * 建立经过认证的到目标主机的降道 Socket。
     */
    private Socket socks5Handshake(String proxyHost, int proxyPort,
                                   String targetHost, int targetPort,
                                   String username, String password) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(proxyHost, proxyPort), 10_000);
        OutputStream out = socket.getOutputStream();
        InputStream  in  = socket.getInputStream();

        // 第一步：版本协商，告知代理支持的认证方式
        // VER=0x05, NMETHODS=0x02, METHODS=[0x00(无认证), 0x02(用户密码)]
        out.write(new byte[]{0x05, 0x02, 0x00, 0x02});
        out.flush();

        byte[] resp = in.readNBytes(2);
        if (resp[0] != 0x05) {
            socket.close();
            throw new IOException("SOCKS5 版本协商失败，服务器返回非 SOCKS5 响应");
        }

        if (resp[1] == 0x02) {
            // 第二步：用户密码子协商 RFC 1929
            // VER=0x01, ULEN, UNAME, PLEN, PASSWD
            byte[] user = username.getBytes(StandardCharsets.UTF_8);
            byte[] pass = password.getBytes(StandardCharsets.UTF_8);
            byte[] authReq = new byte[3 + user.length + pass.length];
            authReq[0] = 0x01;
            authReq[1] = (byte) user.length;
            System.arraycopy(user, 0, authReq, 2, user.length);
            authReq[2 + user.length] = (byte) pass.length;
            System.arraycopy(pass, 0, authReq, 3 + user.length, pass.length);
            out.write(authReq);
            out.flush();

            // STATUS=0x00 表示认证成功
            byte[] authResp = in.readNBytes(2);
            if (authResp[1] != 0x00) {
                socket.close();
                throw new IOException("SOCKS5 用户密码认证失败，用户名或密码错误");
            }
        } else if (resp[1] == (byte) 0xFF) {
            socket.close();
            throw new IOException("SOCKS5 代理拒绝所有认证方式");
        }
        // resp[1] == 0x00：无认证，直接继续

        // 第三步：发送 CONNECT 请求建立到目标的隔道
        // VER=0x05, CMD=0x01(CONNECT), RSV=0x00, ATYP=0x03(域名)
        byte[] hostBytes = targetHost.getBytes(StandardCharsets.UTF_8);
        byte[] connReq = new byte[7 + hostBytes.length];
        connReq[0] = 0x05;
        connReq[1] = 0x01;
        connReq[2] = 0x00;
        connReq[3] = 0x03;
        connReq[4] = (byte) hostBytes.length;
        System.arraycopy(hostBytes, 0, connReq, 5, hostBytes.length);
        connReq[5 + hostBytes.length] = (byte) (targetPort >> 8);
        connReq[6 + hostBytes.length] = (byte) (targetPort & 0xFF);
        out.write(connReq);
        out.flush();

        // 读取代理响应，REP=0x00 表示成功
        byte[] connResp = in.readNBytes(4);
        if (connResp[1] != 0x00) {
            socket.close();
            throw new IOException("SOCKS5 CONNECT 失败，代理响应码：" + (connResp[1] & 0xFF));
        }
        // 跳过 BND.ADDR + BND.PORT 字段
        int skip = switch (connResp[3]) {
            case 0x01 -> 4 + 2;           // IPv4
            case 0x04 -> 16 + 2;          // IPv6
            case 0x03 -> in.read() + 2;   // 域名：先读长度字节，再加 2 字节端口
            default   -> throw new IOException("SOCKS5 未知地址类型：" + connResp[3]);
        };
        in.readNBytes(skip);

        return socket;
    }
}