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
    ENABLE_DAILY_BROADCAST("Y107", "是否开启每日播报", SysCfgTypeEnum.SYS_INIT_CFG),
    DAILY_BROADCAST_CRON("Y108", "每日播报cron", SysCfgTypeEnum.SYS_INIT_CFG),
    ENABLED_VERSION_UPDATE_NOTIFICATIONS("Y109", "是否开启版本更新通知", SysCfgTypeEnum.SYS_INIT_CFG),
    SILICONFLOW_AI_API("Y110", "硅基流动API", SysCfgTypeEnum.SYS_INIT_CFG),
    BOOT_BROADCAST_TOKEN("Y111", "开机播报Token", SysCfgTypeEnum.SYS_INIT_CFG),
    SYS_VNC("Y112", "实例VNC连接url", SysCfgTypeEnum.SYS_INIT_CFG),
    GOOGLE_ONE_CLICK_LOGIN("Y113", "谷歌一键登录所需参数", SysCfgTypeEnum.SYS_INIT_CFG),

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
