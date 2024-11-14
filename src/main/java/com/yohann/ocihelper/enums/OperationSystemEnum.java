package com.yohann.ocihelper.enums;

import lombok.Getter;

/**
 * <p>
 * OperationSystemEnum
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/7 16:10
 */
@Getter
public enum OperationSystemEnum {
    /**
     * 系统镜像类型及版本
     */
    ORACLE_LINUX("Oracle Autonomous Linux", "9"),
    UBUNTU_20_04("Canonical Ubuntu", "20.04"),
    UBUNTU_20_04_MINIMAL("Canonical Ubuntu", "20.04 Minimal"),
    UBUNTU_22_04("Canonical Ubuntu", "22.04"),
    UBUNTU_22_04_MINIMAL("Canonical Ubuntu", "22.04 Minimal"),
    UBUNTU_22_04_MINIMAL_AARCH64("Canonical Ubuntu", "22.04 Minimal aarch64"),
    UBUNTU_24_04("Canonical Ubuntu", "24.04"),
    CENT_OS_7("CentOS", "7"),
    CENT_OS_8_STREAM("CentOS", "8 Stream"),

    ;

    OperationSystemEnum(String type, String version) {
        this.type = type;
        this.version = version;
    }

    private String type;
    private String version;

    public static  OperationSystemEnum getSystemType(String type){
        OperationSystemEnum[] values = OperationSystemEnum.values();
        for (OperationSystemEnum value : values) {
            if (value.getType().equals(type)){
                if (value.getType().equals("Canonical Ubuntu")){
                    return OperationSystemEnum.UBUNTU_20_04;
                }
            }
        }
        return OperationSystemEnum.UBUNTU_20_04;
    }
}
