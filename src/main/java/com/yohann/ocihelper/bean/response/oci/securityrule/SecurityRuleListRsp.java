package com.yohann.ocihelper.bean.response.oci.securityrule;

import com.oracle.bmc.core.model.EgressSecurityRule;
import com.oracle.bmc.core.model.IngressSecurityRule;
import lombok.Data;

import java.util.List;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.response.oci.securityrule
 * @className: SecurityRuleListRsp
 * @author: Yohann
 * @date: 2025/3/1 16:04
 */
@Data
public class SecurityRuleListRsp {

    private List<IngressSecurityRule> ingressSecurityRules;
    private List<EgressSecurityRule> egressSecurityRules;
}
