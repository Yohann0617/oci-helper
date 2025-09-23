package com.yohann.ocihelper.bean.params.sys;

import lombok.Data;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params.sys
 * @className: UpdateLoginCfgParams
 * @author: Yohann
 * @date: 2024/11/30 18:22
 */
@Data
public class UpdateSysCfgParams {

    private String dingToken;
    private String dingSecret;
    private String tgChatId;
    private String tgBotToken;
    private Boolean enableMfa;

    private Boolean enableDailyBroadcast;
    private String dailyBroadcastCron;
    private Boolean enableVersionInform;

    private String gjAiApi;
}
