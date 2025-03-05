package com.yohann.ocihelper.bean.params.oci.securityrule;

import com.oracle.bmc.core.model.EgressSecurityRule;
import com.oracle.bmc.core.model.IcmpOptions;
import com.oracle.bmc.core.model.IngressSecurityRule;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @ClassName UpdateSecurityRuleListParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-02-21 17:20
 **/
@Data
public class UpdateSecurityRuleListParams {

    private List<IngressRule> ingressRuleList = new ArrayList<>();
    private List<EgressRule> egressRuleList = new ArrayList<>();

    @Data
    public static class IngressRule {
//        private Integer icmpCode;
//        private Integer icmpType;
        private IcmpOptions icmpOptions;
        private Boolean isStateless;
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

//        private String sourcePort;
//        private String destinationPort;
        private String description;

    }

    @Data
    public static class EgressRule {
//        private Integer icmpCode;
//        private Integer icmpType;
        private IcmpOptions icmpOptions;
        private String destination;
        private String destinationType;
        private Boolean isStateless;
        private String protocol;

        private Integer tcpSourcePortMin;
        private Integer tcpSourcePortMax;
        private Integer tcpDesPortMin;
        private Integer tcpDesPortMax;
        private Integer udpSourcePortMin;
        private Integer udpSourcePortMax;
        private Integer udpDesPortMin;
        private Integer udpDesPortMax;

//        private String sourcePort;
//        private String destinationPort;
        private String description;
    }
}
