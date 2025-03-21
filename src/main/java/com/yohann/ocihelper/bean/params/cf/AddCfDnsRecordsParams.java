package com.yohann.ocihelper.bean.params.cf;

import lombok.Data;

/**
 * @ClassName AddCfDnsRecordsParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-19 14:41
 **/
@Data
public class AddCfDnsRecordsParams {

    private String domainPrefix;
    /**
     * A or AAAA
     */
    private String type;
    private boolean proxied;
    private String ipAddress;
    private String zoneId;
    private String apiToken;
    private String comment;
    /**
     * >=60
     */
    private int ttl;
}
