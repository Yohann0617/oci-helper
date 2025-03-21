package com.yohann.ocihelper.bean.params.cf;

import lombok.Data;

/**
 * @ClassName UpdateCfDnsRecordsParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-21 15:23
 **/
@Data
public class UpdateCfDnsRecordsParams {

    private String zoneId;
    private String apiToken;
    private String id;
    private String name;
    private String type;
    private String ipAddress;
    private boolean proxied;
    private int ttl;
    private String comment;
}
