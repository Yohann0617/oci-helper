package com.yohann.ocihelper.bean.params.cf;

import lombok.Data;

/**
 * @ClassName GetCfDnsRecordsParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-19 16:47
 **/
@Data
public class GetCfDnsRecordsParams {

    /**
     * A or AAAA
     */
    private String type;
    private String domain;
    private String zoneId;
    private String apiToken;
}
