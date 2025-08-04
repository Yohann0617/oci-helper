package com.yohann.ocihelper.enums;

import lombok.Getter;

/**
 * @ClassName IpDataTypeEnum
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-06 16:14
 **/
@Getter
public enum IpDataTypeEnum {

    IP_DATA_ORACLE("oracle", "oracle cloud ip类型"),
    ;

    private String code;
    private String desc;

    IpDataTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
