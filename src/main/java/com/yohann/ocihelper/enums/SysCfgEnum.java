package com.yohann.ocihelper.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.enums
 * @className: SysCfgEnum
 * @author: Yohann
 * @date: 2024/11/30 17:29
 */
@Getter
public enum SysCfgEnum {

    /**
     * 系统配置项
     */
    SYS_TG_BOT_TOKEN("Y101", "telegram机器人token", SysCfgTypeEnum.SYS_INIT_CFG),
    SYS_TG_CHAT_ID("Y102", "telegram个人ID", SysCfgTypeEnum.SYS_INIT_CFG),
    SYS_DING_BOT_TOKEN("Y103", "钉钉机器人accessToken", SysCfgTypeEnum.SYS_INIT_CFG),
    SYS_DING_BOT_SECRET("Y104", "钉钉机器人secret", SysCfgTypeEnum.SYS_INIT_CFG),
    SYS_MFA_SECRET("Y105", "谷歌MFA", SysCfgTypeEnum.SYS_MFA_CFG),

    SYS_INFO_VERSION("Y106", "系统版本号", SysCfgTypeEnum.SYS_INFO),


    ;

    SysCfgEnum(String code, String desc, SysCfgTypeEnum type) {
        this.code = code;
        this.desc = desc;
        this.type = type;
    }

    private String code;
    private String desc;
    private SysCfgTypeEnum type;


    public static List<SysCfgEnum> getCodeListByType(SysCfgTypeEnum type) {
        return Arrays.stream(values())
                .filter(x -> x.getType() == type)
                .collect(Collectors.toList());
    }
}
