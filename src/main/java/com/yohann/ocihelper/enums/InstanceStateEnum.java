package com.yohann.ocihelper.enums;

import lombok.Getter;

/**
 * <p>
 * InstanceStateEnum
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/8 13:08
 */
@Getter
public enum InstanceStateEnum {

    LIFECYCLE_STATE_RUNNING("RUNNING","运行中"),
    LIFECYCLE_STATE_TERMINATED("TERMINATED","已终止")
    ;

    private String state;
    private String desc;

    InstanceStateEnum(String state, String desc) {
        this.state = state;
        this.desc = desc;
    }
}
