package com.yohann.ocihelper.bean.params.cf;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @ClassName OciUpdateCfDnsRecordsParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-21 16:09
 **/
@Data
public class OciUpdateCfDnsRecordsParams {

    @NotBlank(message = "配置ID不能为空")
    private String cfCfgId;
    @NotBlank(message = "ID不能为空")
    private String id;
    @NotBlank(message = "域名前缀不能为空")
    private String name;
    @NotBlank(message = "类型不能为空")
    private String type;
    @NotBlank(message = "ip地址不能为空")
    private String ipAddress;
    @NotNull(message = "是否代理不能为空")
    private Boolean proxied;
    @NotNull(message = "ttl不能为空")
    @Min(value = 60)
    private Integer ttl;
    private String comment;
}
