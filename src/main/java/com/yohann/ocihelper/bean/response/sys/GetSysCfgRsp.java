package com.yohann.ocihelper.bean.response.sys;

import lombok.Data;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.response.sys
 * @className: GetSysCfgRsp
 * @author: Yohann
 * @date: 2024/11/30 20:14
 */
@Data
public class GetSysCfgRsp {

    private String dingToken;
    private String dingSecret;
    private String tgChatId;
    private String tgBotToken;
    private boolean enableMfa;
    private String mfaSecret;
    private String mfaQrData;

    private String enableDailyBroadcast;
    private String dailyBroadcastCron;
    private String enableVersionInform;
}
