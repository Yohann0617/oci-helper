package com.yohann.ocihelper.bean.params.cf;

import lombok.Data;

import javax.validation.constraints.Min;

/**
 * @ClassName OciAddCfDnsRecordsParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-21 14:25
 **/
@Data
public class OciAddCfDnsRecordsParams {

    private String cfCfgId;
    private String prefix;
    private String type;
    private String ipAddress;
    private Boolean proxied;
    @Min(value = 60)
    private Integer ttl;
    private String comment;
}
