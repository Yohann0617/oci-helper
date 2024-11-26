package com.yohann.ocihelper.enums;

import lombok.Getter;

/**
 * <p>
 * InstanceActionEnum
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/26 11:22
 */
@Getter
public enum InstanceActionEnum {
    /**
     * 实例操作
     */
    ACTION_STOP("STOP", "关闭实例"),
    ACTION_START("START", "启动实例"),
    ACTION_RESET("RESET", "关闭实例然后重新打开"),

    ;

    private String action;
    private String desc;

    InstanceActionEnum(String action, String desc) {
        this.action = action;
        this.desc = desc;
    }
}
