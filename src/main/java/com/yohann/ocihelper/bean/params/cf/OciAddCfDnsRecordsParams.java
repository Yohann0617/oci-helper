package com.yohann.ocihelper.bean.params.cf;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @ClassName OciAddCfDnsRecordsParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-21 14:25
 **/
@Data
public class OciAddCfDnsRecordsParams {

    @NotBlank(message = "配置ID不能为空")
    private String cfCfgId;
    @NotBlank(message = "域名前缀不能为空")
    private String prefix;
    @NotBlank(message = "类型不能为空")
    private String type;
    @NotBlank(message = "ip地址不能为空")
    private String ipAddress;
//    @NotNull(message = "是否代理不能为空")
    private boolean proxied;
//    @NotNull(message = "ttl不能为空")
//    @Min(value = 60)
    private Integer ttl;
    private String comment;
}
