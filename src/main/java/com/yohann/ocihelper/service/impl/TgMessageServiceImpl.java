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

import static cn.hutool.core.io.FileUtil.readLine;

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
    private static final int TG_PORT = 443;
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
        // 获取全局代理配置
        OciKv proxyKv = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_PROXY.getCode()));
        String proxyUrl = proxyKv != null ? proxyKv.getValue() : null;

        try {
            String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);
            String urlString = String.format(TG_URL, botToken, chatId, encodedMessage);

            // 带认证的代理需要手动建立隧道并直接发送，此时无需再调用 request.execute()。
            // sendDirectly 返回 true 表示消息已由隧道发送完毕，直接返回。
            if (StrUtil.isNotBlank(proxyUrl) && sendViaProxy(urlString, proxyUrl)) {
                return;
            }

            // 无代理或无认证 HTTP 代理：走 Hutool 标准流程
            HttpRequest request = HttpUtil.createGet(urlString);
            if (StrUtil.isNotBlank(proxyUrl)) {
                applyNoAuthProxy(request, proxyUrl);
            }
            HttpResponse response = request.execute();
            if (response.getStatus() == 200) {
                log.info("Telegram 消息发送成功！");
            } else {
                log.info("Telegram 消息发送失败，响应码：[{}]", response.getStatus());
            }
        } catch (Exception e) {
            log.error("发送 Telegram 消息时发生异常：", e);
        }
    }

    /**
     * 对带认证的代理（HTTP 带认证 / SOCKS5 带认证）使用手动隧道方式直接发送消息。
     * @return true 表示已通过手动隧道发送完毕；false 表示属于无认证代理，需交由调用方继续处理。
     */
    private boolean sendViaProxy(String urlString, String proxyUrl) {
        try {
            URI uri = new URI(proxyUrl.trim());
            String scheme   = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            String host     = uri.getHost();
            int    port     = uri.getPort();
            String userInfo = uri.getUserInfo();

            if (StrUtil.isBlank(host) || port <= 0) {
                log.warn("Telegram 代理地址格式不正确，已忽略：{}", proxyUrl);
                return false;
            }

            if (("http".equals(scheme) || "https".equals(scheme)) && StrUtil.isNotBlank(userInfo)) {
                // 带认证的 HTTP 代理：手动 CONNECT 隧道 + TLS
                int    idx       = userInfo.indexOf(':');
                String proxyUser = idx >= 0 ? userInfo.substring(0, idx) : userInfo;
                String proxyPass = idx >= 0 ? userInfo.substring(idx + 1) : "";
                sendViaHttpConnectTunnel(urlString, host, port, proxyUser, proxyPass);
                log.debug("Telegram 消息已通过带认证 HTTP 代理（手动 CONNECT 隧道）发送，用户：{}", proxyUser);
                return true;
            }

            if (scheme.startsWith("socks") && StrUtil.isNotBlank(userInfo)) {
                // 带认证的 SOCKS5 代理：手动握手隧道 + TLS
                int    idx  = userInfo.indexOf(':');
                String user = idx >= 0 ? userInfo.substring(0, idx) : userInfo;
                String pwd  = idx >= 0 ? userInfo.substring(idx + 1) : "";
                sendViaSocks5Tunnel(urlString, host, port, user, pwd);
                log.debug("Telegram 消息已通过带认证 SOCKS5 代理发送，用户：{}", user);
                return true;
            }

            // 无认证代理，交由调用方处理
            return false;
        } catch (Exception e) {
            log.warn("通过代理发送 Telegram 消息失败，已忽略 [{}]：{}", proxyUrl, e.getMessage());
            return true; // 避免再次尝试直连
        }
    }

    /**
     * 对无认证代理（HTTP / SOCKS5）将代理信息设置到 Hutool HttpRequest 上。
     */
    private void applyNoAuthProxy(HttpRequest request, String proxyUrl) {
        try {
            URI uri = new URI(proxyUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            String host   = uri.getHost();
            int    port   = uri.getPort();

            if (StrUtil.isBlank(host) || port <= 0) {
                return;
            }
            if ("http".equals(scheme) || "https".equals(scheme)) {
                request.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
                log.debug("Telegram 消息发送使用无认证 HTTP 代理：{}", proxyUrl);
            } else if (scheme.startsWith("socks")) {
                request.setProxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port)));
                log.debug("Telegram 消息发送使用无认证 SOCKS5 代理：{}", proxyUrl);
            }
        } catch (Exception e) {
            log.warn("设置无认证代理失败，已忽略 [{}]：{}", proxyUrl, e.getMessage());
        }
    }

    /**
     * 手动向 HTTP 代理发送带 Proxy-Authorization 头的 CONNECT 请求建立隧道，
     * 再叠加 TLS，最终在 SSL Socket 上直接发送 HTTP 报文。
     * 完全绕过 JDK/Hutool 的自动代理认证机制，兼容 JDK 21 虚拟线程。
     */
    private void sendViaHttpConnectTunnel(String urlString,
                                          String proxyHost, int proxyPort,
                                          String username, String password) throws IOException {
        Socket tunnel = new Socket();
        tunnel.connect(new InetSocketAddress(proxyHost, proxyPort), 10_000);
        try {
            OutputStream out = tunnel.getOutputStream();
            InputStream  in  = tunnel.getInputStream();

            // 构造携带 Proxy-Authorization 的 CONNECT 请求
            String credentials = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            String connectReq = "CONNECT " + TG_HOST + ":" + TG_PORT + " HTTP/1.1\r\n"
                    + "Host: " + TG_HOST + ":" + TG_PORT + "\r\n"
                    + "Proxy-Authorization: Basic " + credentials + "\r\n"
                    + "Proxy-Connection: keep-alive\r\n"
                    + "\r\n";
            out.write(connectReq.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // 读取代理响应状态行，200 表示隧道建立成功
            String statusLine = readLine(in);
            if (statusLine == null || !statusLine.contains("200")) {
                throw new IOException("HTTP 代理 CONNECT 失败，响应：" + statusLine);
            }
            // 跳过剩余响应头，直到空行
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                // 忽略响应头字段
            }

            // 在隧道上叠加 TLS
            SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(tunnel, TG_HOST, TG_PORT, true);
            sslSocket.startHandshake();
            // 在已建立的 SSL Socket 上直接发送 HTTP/1.1 报文
            sendHttpOverSocket(sslSocket, urlString);
        } catch (IOException e) {
            tunnel.close();
            throw e;
        }
    }

    /**
     * 从输入流中逐字节读取一行（以 CRLF 或 LF 结尾），返回不含换行符的字符串。
     * 流结束时返回 null。
     */
    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                // 去掉末尾可能存在的 '\r'
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\r') {
                    sb.deleteCharAt(sb.length() - 1);
                }
                return sb.toString();
            }
            sb.append((char) b);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 手动完成 SOCKS5 握手建立隧道 Socket，再叠加 TLS，
     * 然后直接在该 Socket 上发送 HTTP 报文。
     * 绕过 Hutool 和 JDK 内置的 SOCKS5 认证机制，完全线程安全。
     */
    private void sendViaSocks5Tunnel(String urlString,
                                     String proxyHost, int proxyPort,
                                     String username, String password) throws IOException {
        // 手动完成 SOCKS5 握手，得到经过认证的原始 TCP 隧道
        Socket tunnel = socks5Handshake(proxyHost, proxyPort, TG_HOST, TG_PORT, username, password);
        try {
            // 在隧道上叠加 TLS（Telegram 使用 HTTPS/443）
            SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            // createSocket 第四个参数 autoClose=true，表示 sslSocket.close() 时会自动关闭底层 tunnel
            SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(tunnel, TG_HOST, TG_PORT, true);
            sslSocket.startHandshake();
            // 在已建立的 SSL Socket 上直接发送 HTTP/1.1 报文（内部会关闭 sslSocket）
            sendHttpOverSocket(sslSocket, urlString);
        } catch (IOException e) {
            // createSocket 或 startHandshake 失败时 tunnel 尚未被 autoClose 覆盖，需手动关闭
            tunnel.close();
            throw e;
        }
    }

    /**
     * 在已建立的 SSL Socket 上直接发送 HTTP/1.1 GET 请求并读取响应状态行。
     * 使用 try-with-resources 确保任意路径下 sslSocket 均会被关闭，无资源泄漏风险。
     * 适用于 HTTP 代理 CONNECT 隧道和 SOCKS5 隧道两种场景。
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
            InputStream in = sslSocket.getInputStream();

            out.write(rawRequest.getBytes(StandardCharsets.UTF_8));
            out.flush();

            // 逐字节读取 HTTP 响应状态行，遇到 CRLF 结束
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
                    log.info("Telegram 消息发送成功（经由 SOCKS5 隧道）");
                } else {
                    log.info("Telegram 消息发送失败（SOCKS5 隧道），响应码：[{}]", code);
                }
            }
        }
    }

    /**
     * 手动完成 SOCKS5 RFC 1928 版本协商 + RFC 1929 用户密码子协商握手，
     * 建立经过认证的、通向目标主机的隧道 Socket。
     */
    private Socket socks5Handshake(String proxyHost, int proxyPort,
                                   String targetHost, int targetPort,
                                   String username, String password) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(proxyHost, proxyPort), 10_000);
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

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

        // 第三步：发送 CONNECT 请求建立到目标的隧道
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
            default -> throw new IOException("SOCKS5 未知地址类型：" + connResp[3]);
        };
        in.readNBytes(skip);

        return socket;
    }
}