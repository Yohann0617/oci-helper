package com.yohann.ocihelper.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Set;

/**
 * 自定义 JWT 工具类，仅支持 HS256 算法，显式拒绝 alg=none 等不安全算法
 *
 * @author Yohann
 * @date 2024/11/7
 */
public class JwtUtils {

    private static final Set<String> ALLOWED_ALGORITHMS = Set.of("HS256");
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder B64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

    /**
     * 生成 HS256 签名的 JWT token
     *
     * @param payload   载荷数据
     * @param secretKey 签名密钥
     * @return JWT token 字符串
     */
    public static String genToken(Map<String, Object> payload, String secretKey) {
        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String headerB64 = B64_ENCODER.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));

        String payloadJson = mapToJson(payload);
        String payloadB64 = B64_ENCODER.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        String signingInput = headerB64 + "." + payloadB64;
        byte[] signature = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), secretKey.getBytes(StandardCharsets.UTF_8));
        String signatureB64 = B64_ENCODER.encodeToString(signature);

        return signingInput + "." + signatureB64;
    }

    /**
     * 生成带过期时间的 JWT token
     *
     * @param payload   载荷数据（不含 exp）
     * @param secretKey 签名密钥
     * @param hours     过期小时数
     * @return JWT token 字符串
     */
    public static String genToken(Map<String, Object> payload, String secretKey, int hours) {
        ZoneId zoneId = ZoneId.systemDefault();
        Instant instant = LocalDateTime.now().plusHours(hours).atZone(zoneId).toInstant();
        payload.put("exp", instant.getEpochSecond());
        return genToken(payload, secretKey);
    }

    /**
     * 验证 JWT token 签名和算法
     *
     * @param token     JWT token
     * @param secretKey 签名密钥
     * @return 验证通过返回 true
     */
    public static boolean verifyToken(String token, String secretKey) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }

            // 解析 header 并校验算法，显式拒绝 alg=none
            String headerJson = new String(B64_DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            String alg = extractJsonString(headerJson, "alg");
            if (alg == null || !ALLOWED_ALGORITHMS.contains(alg)) {
                return false;
            }

            // 重新计算签名并常量时间比较
            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSig = hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), secretKey.getBytes(StandardCharsets.UTF_8));
            byte[] actualSig = B64_DECODER.decode(parts[2]);

            return MessageDigest.isEqual(expectedSig, actualSig);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查 token 是否过期（不验证签名，仅用于快速预检）
     *
     * @param token JWT token
     * @return 过期返回 true
     */
    public static boolean isTokenExpired(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return true;
            }
            String payloadJson = new String(B64_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            String expStr = extractJsonString(payloadJson, "exp");
            if (expStr == null) {
                return true;
            }
            long exp = Long.parseLong(expStr);
            return exp < System.currentTimeMillis() / 1000;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 从载荷中获取指定字段值
     *
     * @param token JWT token
     * @param key   字段名
     * @return 字段值，不存在返回 null
     */
    public static String getPayloadClaim(String token, String key) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            String payloadJson = new String(B64_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            return extractJsonString(payloadJson, key);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * HMAC-SHA256 签名
     */
    private static byte[] hmacSha256(byte[] data, byte[] key) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(key, HMAC_SHA256);
            mac.init(keySpec);
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 签名失败", e);
        }
    }

    /**
     * 简单 Map 转 JSON 字符串（仅处理 String、Number、Boolean 类型值）
     */
    private static String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            appendJsonValue(sb, entry.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 将 Java 对象追加为 JSON 值
     */
    private static void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append("\"").append(escapeJson((String) value)).append("\"");
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Date) {
            sb.append(((Date) value).getTime() / 1000);
        } else {
            sb.append("\"").append(escapeJson(value.toString())).append("\"");
        }
    }

    /**
     * JSON 字符串转义
     */
    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 从简单 JSON 字符串中提取指定 key 的字符串值
     */
    private static String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\"";
        int keyIdx = json.indexOf(searchKey);
        if (keyIdx < 0) {
            return null;
        }
        int colonIdx = json.indexOf(":", keyIdx + searchKey.length());
        if (colonIdx < 0) {
            return null;
        }
        int start = colonIdx + 1;
        // 跳过空白
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) {
            return null;
        }
        char firstChar = json.charAt(start);
        if (firstChar == '"') {
            // 字符串值
            int end = start + 1;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == '\\') {
                    end += 2;
                } else if (c == '"') {
                    break;
                } else {
                    end++;
                }
            }
            return json.substring(start + 1, end);
        } else {
            // 数字或布尔值
            int end = start;
            while (end < json.length() && !Character.isWhitespace(json.charAt(end)) && json.charAt(end) != ',' && json.charAt(end) != '}') {
                end++;
            }
            return json.substring(start, end);
        }
    }
}