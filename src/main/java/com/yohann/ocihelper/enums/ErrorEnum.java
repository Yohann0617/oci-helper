package com.yohann.ocihelper.enums;

import lombok.Getter;

/**
 * <p>
 * ErrorEnum
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/7 18:55
 */
@Getter
public enum ErrorEnum {

    LIMIT_EXCEEDED(400, "limit", "无法创建实例，配额已经超过免费额度"),
    NOT_AUTHENTICATED(401, "NotAuthenticated", "账号已无权或已被封禁"),
    TOO_MANY_REQUESTS(429, "TooManyRequests", "请求频繁"),
    CAPACITY(500, "Out of capacity", "Out of capacity"),
    CAPACITY_HOST(500, "Out of host capacity", "Out of host capacity"),

    ;

    private final int code;
    private final String errorType;
    private final String message;

    ErrorEnum(int code, String errorType, String message) {
        this.code = code;
        this.errorType = errorType;
        this.message = message;
    }
}
