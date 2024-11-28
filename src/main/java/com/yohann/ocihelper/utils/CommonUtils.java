package com.yohann.ocihelper.utils;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.net.Ipv4Util;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.exception.OciException;
import org.springframework.validation.BindingResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * CommonUtils
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/7 18:54
 */
public class CommonUtils {

    public static final String CREATE_TASK_PREFIX = "CREATE_TASK_PREFIX_";
    public static final String CHANGE_IP_TASK_PREFIX = "CREATE_TASK_PREFIX_";
    public static final String CREATE_COUNTS_PREFIX = "CREATE_COUNTS_PREFIX_";
    public static final String CHANGE_IP_ERROR_COUNTS_PREFIX = "CHANGE_IP_ERROR_COUNTS_PREFIX_";
    public static final String TERMINATE_INSTANCE_PREFIX = "TERMINATE_INSTANCE_PREFIX_";
    public static final String LOG_FILE_PATH = "/var/log/oci-helper.log";
    private static final String CIDR_REGEX =
            "^([0-9]{1,3}\\.){3}[0-9]{1,3}/([0-9]|[1-2][0-9]|3[0-2])$";
    private static final Pattern CIDR_PATTERN = Pattern.compile(CIDR_REGEX);
    public static final DateTimeFormatter DATETIME_FMT_PURE = DateTimeFormatter.ofPattern(DatePattern.PURE_DATETIME_PATTERN);

    public static final String BEGIN_CREATE_MESSAGE_TEMPLATE =
            "【开机任务】 用户：[%s] 开始执行开机任务\n\n" +
                    "时间： %s\n" +
                    "Region： %s\n" +
                    "CPU类型： %s\n" +
                    "CPU： %s\n" +
                    "内存（GB）： %s\n" +
                    "磁盘大小（GB）： %s\n" +
                    "数量： %s\n" +
                    "root密码： %s";
    public static final String BEGIN_CHANGE_IP_MESSAGE_TEMPLATE =
            "【更换IP任务】 用户：[%s] 开始执行更换公网IP任务\n\n" +
                    "时间： %s\n" +
                    "区域： %s\n" +
                    "实例： %s\n" +
                    "当前公网IP： %s";
    public static final String CHANGE_IP_MESSAGE_TEMPLATE =
            "【更换IP任务】 🎉 用户：[%s] 更换公共IP成功 🎉\n\n" +
                    "时间： %s\n" +
                    "区域： %s\n" +
                    "实例： %s\n" +
                    "新的公网IP： %s";
    public static final String TERMINATE_INSTANCE_MESSAGE_TEMPLATE =
            "【终止实例任务】 用户：[%s] 正在执行终止实例任务 \n\n" +
                    "时间： %s\n" +
                    "区域： %s\n" +
                    "请耐心等待，稍后自行刷新详情查看";

    public static final String TERMINATE_INSTANCE_CODE_MESSAGE_TEMPLATE =
            "【验证码】 用户：[%s] 正在执行终止实例任务 \n\n" +
                    "时间： %s\n" +
                    "区域： %s\n" +
                    "实例： %s\n" +
                    "Shape： %s\n" +
                    "验证码： %s\n" +
                    "⭐注意：终止实例后，数据无法恢复，请谨慎操作！！！";

    public static List<OciUser> parseConfigContent(String configContent) throws IOException {
        // 检查并移除 UTF-8 BOM
//        if (configContent.startsWith("\uFEFF")) {
//            configContent = configContent.substring(1);
//        }

        List<OciUser> ociUsers = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(configContent))) {
            String line;
            OciUser currentUser = null;
            String currentUsername = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty()) {
                    continue; // Skip empty lines
                }

                if (line.startsWith("[")) {
                    // New section
                    if (currentUser != null) {
                        ociUsers.add(currentUser);
                    }
                    currentUsername = line.substring(1, line.length() - 1); // Remove square brackets
                    currentUser = new OciUser();
                    currentUser.setUsername(currentUsername);
                } else if (currentUser != null && line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    String key = parts[0].trim();
                    String value = parts[1].trim();

                    switch (key) {
                        case "user":
                            currentUser.setOciUserId(value);
                            break;
                        case "fingerprint":
                            currentUser.setOciFingerprint(value);
                            break;
                        case "tenancy":
                            currentUser.setOciTenantId(value);
                            break;
                        case "region":
                            currentUser.setOciRegion(value);
                            break;
                        case "key_file":
                            currentUser.setOciKeyPath(value);
                            break;
                        default:
                            // Ignore unknown keys
                            break;
                    }
                }
            }

            // Add the last user
            if (currentUser != null) {
                ociUsers.add(currentUser);
            }
        }
        return ociUsers;
    }

    public static String getMD5(String input) {
        try {
            // Create MD5 MessageDigest instance
            MessageDigest md = MessageDigest.getInstance("MD5");

            // Convert input string to bytes and compute hash
            byte[] messageDigest = md.digest(input.getBytes());

            // Convert byte array to hexadecimal String
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String genToken(Map<String, Object> payload, String secretKey) {
        ZoneId zoneId = ZoneId.systemDefault();
        Instant instant = LocalDateTime.now().plusHours(3).atZone(zoneId).toInstant();

        return JWT.create()
                .addHeaders(null)
                .addPayloads(payload)
                .setKey(secretKey.getBytes())
                .setExpiresAt(Date.from(instant))
                .sign();
    }

    public static boolean isTokenExpired(String token) {
        JWT jwt = JWTUtil.parseToken(token);

        Long exp = Long.parseLong(String.valueOf(jwt.getPayload("exp")));
        if (exp != null) {
            return exp < System.currentTimeMillis() / 1000; // 将毫秒转换为秒
        }
        return true;
    }

    public static String dateFmt2String(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat(DatePattern.NORM_DATETIME_PATTERN);
        return formatter.format(date);
    }

    /**
     * 校验输入的 CIDR 字符串是否为合法网段
     *
     * @param cidr CIDR 字符串 (例如 "192.168.1.0/24")
     * @return true 如果 CIDR 是合法的，否则 false
     */
    public static boolean isValidCidr(String cidr) {
        // 先匹配基本的 CIDR 正则格式
        Matcher matcher = CIDR_PATTERN.matcher(cidr);
        if (!matcher.matches()) {
            return false;
        }

        // 拆分 IP 地址和子网掩码部分
        String[] parts = cidr.split("/");
        String ip = parts[0];
        int prefixLength = Integer.parseInt(parts[1]);

        return isValidIp(ip) && isValidPrefixLength(prefixLength);
    }

    /**
     * 检查 IP 地址是否有效（每个字节 0–255）
     *
     * @param ip IP 地址字符串
     * @return true 如果 IP 地址有效，否则 false
     */
    private static boolean isValidIp(String ip) {
        try {
            InetAddress inet = InetAddress.getByName(ip);
            String[] octets = ip.split("\\.");
            if (octets.length != 4) {
                return false;
            }
            for (String octet : octets) {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return !inet.isMulticastAddress();  // 排除组播地址
        } catch (UnknownHostException | NumberFormatException e) {
            return false;
        }
    }

    /**
     * 检查子网掩码前缀是否在 0 到 32 的范围内
     *
     * @param prefixLength 子网掩码前缀
     * @return true 如果前缀长度有效，否则 false
     */
    private static boolean isValidPrefixLength(int prefixLength) {
        return prefixLength >= 0 && prefixLength <= 32;
    }

    public static <T> Page<T> buildPage(List<T> entities, long size, long current, long total) {
        Page<T> page = new Page<>();
        page.setRecords(entities);
        page.setSize(size);
        page.setCurrent(current);
        page.setTotal(total);
        page.setPages((long) (Math.ceil((double) total / size)));
        return page;
    }

    public static void checkAndThrow(BindingResult bindingResult) {
        if (bindingResult != null && bindingResult.hasErrors()) {
            String error = bindingResult.getFieldError() == null ? "" : bindingResult.getFieldError().getDefaultMessage();
            throw new OciException(-1, error);
        }
    }

    public static Map<String, String> getOciCfgFromStr(String content) {
        Properties properties = new Properties();
        Map<String, String> configMap = new HashMap<>();

        try {
            // 使用 StringReader 将字符串内容读取为 Properties
            properties.load(new StringReader(content));

            // 将 Properties 中的内容转换为 Map
            for (String key : properties.stringPropertyNames()) {
                configMap.put(key, properties.getProperty(key));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return configMap;
    }

    public static boolean isIpInCidrList(String ip, List<String> cidrList) {
        long ipLong = Ipv4Util.ipv4ToLong(ip); // 将 IP 转换为 long

        for (String cidr : cidrList) {
            String[] cidrParts = cidr.split("/");
            String cidrIp = cidrParts[0];
            int maskLength = Integer.parseInt(cidrParts[1]);

            long cidrIpLong = Ipv4Util.ipv4ToLong(cidrIp);
            long mask = (1L << (32 - maskLength)) - 1;

            if ((ipLong & ~mask) == (cidrIpLong & ~mask)) {
                return true;
            }
        }
        return false;
    }

    public static String getPwdShell(String passwd) {
        return "#cloud-config\n" +
                "ssh_pwauth: yes\n" +
                "chpasswd:\n" +
                "  list: |\n" +
                "    root:" + passwd + "\n" +
                "  expire: false\n" +
                "write_files:\n" +
                "  - path: /tmp/setup_root_access.sh\n" +
                "    permissions: '0700'\n" +
                "    content: |\n" +
                "      #!/bin/bash\n" +
                "      \n" +
                "      # Detect OS\n" +
                "      if [ -f /etc/os-release ]; then\n" +
                "        . /etc/os-release\n" +
                "        OS=$ID\n" +
                "      else\n" +
                "        echo \"Cannot detect OS, exiting.\"\n" +
                "        exit 1\n" +
                "      fi\n" +
                "      \n" +
                "      # Convert to lowercase\n" +
                "      OS=$(echo \"$OS\" | tr '[:upper:]' '[:lower:]')\n" +
                "      \n" +
                "      # Configure SSH\n" +
                "      sed -i 's/^#\\?PasswordAuthentication.*/PasswordAuthentication yes/' /etc/ssh/sshd_config\n" +
                "      sed -i 's/^#\\?PermitRootLogin.*/PermitRootLogin yes/' /etc/ssh/sshd_config\n" +
                "      \n" +
                "      # Ensure PrintMotd is set to yes\n" +
                "      if grep -q \"^#\\?PrintMotd\" /etc/ssh/sshd_config; then\n" +
                "        sed -i 's/^#\\?PrintMotd.*/PrintMotd yes/' /etc/ssh/sshd_config\n" +
                "      else\n" +
                "        echo \"PrintMotd yes\" >> /etc/ssh/sshd_config\n" +
                "      fi\n" +
                "      # Ensure PrintLastLog is set to yes\n" +
                "      if grep -q \"^#\\?PrintLastLog\" /etc/ssh/sshd_config; then\n" +
                "        sed -i 's/^#\\?PrintLastLog.*/PrintLastLog yes/' /etc/ssh/sshd_config\n" +
                "      else\n" +
                "        echo \"PrintLastLog yes\" >> /etc/ssh/sshd_config\n" +
                "      fi\n\n" +
                "      # Restart SSH service\n" +
                "      if command -v systemctl >/dev/null 2>&1; then\n" +
                "        systemctl restart sshd\n" +
                "      else\n" +
                "        service sshd restart\n" +
                "      fi\n" +
                "      \n" +
                "      # Set up warning message\n" +
                "      {\n" +
                "        echo \"🎉 欢迎使用Y探长~ 🎉\"\n" +
                "        echo \"Source code address: https://github.com/Yohann0617/oci-helper\"\n" +
                "      } | tee /etc/motd\n" +
                "      \n" +
                "      # OS-specific configurations\n" +
                "      case $OS in\n" +
                "        ubuntu|debian)\n" +
                "          # Ubuntu/Debian specific commands\n" +
                "          sed -i 's/^#\\?PermitRootLogin.*/PermitRootLogin yes/' /etc/ssh/sshd_config\n" +
                "          ;;\n" +
                "        ol|rhel|centos|almalinux|rocky)\n" +
                "          # Oracle Linux/RHEL/CentOS/AlmaLinux/Rocky Linux specific commands\n" +
                "          sed -i 's/^#\\?PermitRootLogin.*/PermitRootLogin yes/' /etc/ssh/sshd_config\n" +
                "          ;;\n" +
                "        *)\n" +
                "          echo \"Unsupported OS: $OS\" >&2\n" +
                "          ;;\n" +
                "      esac\n" +
                "runcmd:\n" +
                "  - bash /tmp/setup_root_access.sh\n" +
                "  - rm /tmp/setup_root_access.sh\n";
    }

}
