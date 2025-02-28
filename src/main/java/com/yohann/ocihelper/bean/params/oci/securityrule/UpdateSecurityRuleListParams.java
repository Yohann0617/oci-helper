package com.yohann.ocihelper.bean.params.oci.securityrule;

import lombok.Data;

import java.util.List;

/**
 * @ClassName UpdateSecurityRuleListParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-02-21 17:20
 **/
@Data
public class UpdateSecurityRuleListParams {


    private List<IngressRule> ingressRuleList;
    private List<EgressRule> egressRuleList;

    @Data
    public static class IngressRule {
        private Integer icmpCode;
        private Integer icmpType;
        private boolean isStateless;
        private String protocol;
        private String source;
        private String sourceType;
        private Integer tcpSourcePortMin;
        private Integer tcpSourcePortMax;
        private Integer tcpDesPortMin;
        private Integer tcpDesPortMax;
        private Integer udpSourcePortMin;
        private Integer udpSourcePortMax;
        private Integer udpDesPortMin;
        private Integer udpDesPortMax;
        private String description;
    }

    @Data
    public static class EgressRule {
        private String destination;
        private String destinationType;
        private Integer icmpCode;
        private Integer icmpType;
        private boolean isStateless;
        private String protocol;
        private Integer tcpSourcePortMin;
        private Integer tcpSourcePortMax;
        private Integer tcpDesPortMin;
        private Integer tcpDesPortMax;
        private Integer udpSourcePortMin;
        private Integer udpSourcePortMax;
        private Integer udpDesPortMin;
        private Integer udpDesPortMax;
        private String description;
    }
}
