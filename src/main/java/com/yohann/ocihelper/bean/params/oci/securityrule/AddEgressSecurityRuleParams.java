package com.yohann.ocihelper.bean.params.oci.securityrule;

import com.oracle.bmc.core.model.IcmpOptions;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * @ClassName AddEgressSecurityRuleParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-05 15:50
 **/
@Data
public class AddEgressSecurityRuleParams {

    @NotBlank(message = "api配置id不能为空")
    private String ociCfgId;
    @NotBlank(message = "vcnId不能为空")
    private String vcnId;
    private EgressInfo outboundRule;

    @Data
    public static class EgressInfo{
        private Boolean isStateless;
        private String destinationType;
        private String destination;
        private String protocol;
        private IcmpOptions icmpOptions;
        private String sourcePort;
        private String destinationPort;
        private String description;
    }
}
