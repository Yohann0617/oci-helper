package com.yohann.ocihelper.service;

import com.yohann.ocihelper.bean.params.oci.securityrule.GetSecurityRuleListParams;
import com.yohann.ocihelper.bean.response.oci.securityrule.SecurityRuleListRsp;

public interface ISecurityRuleService {

    SecurityRuleListRsp getSecurityRuleList(GetSecurityRuleListParams params);
}
