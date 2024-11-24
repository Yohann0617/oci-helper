package com.yohann.ocihelper.enums;

import lombok.Getter;

/**
 * <p>
 * OciCfgEnum
 * </p >
 *
 * @author yohann
 * @since 2024/11/8 12:12
 */
@Getter
public enum OciCfgEnum {

    /**
     * OCI配置
     */
    OCI_CFG_USER_ID("userId", "用户id"),
    OCI_CFG_TENANT_ID("tenantId", "租户id"),
    OCI_CFG_REGION("region", "区域"),
    OCI_CFG_FINGERPRINT("fingerprint", "指纹"),
    OCI_CFG_KEY_FILE("key_path", "密钥文件全路径"),
    ;

    private String type;
    private String desc;

    OciCfgEnum(String type, String desc) {
        this.type = type;
        this.desc = desc;
    }
}
