package com.yohann.ocihelper.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yohann.ocihelper.bean.params.oci.securityrule.AddEgressSecurityRuleParams;
import com.yohann.ocihelper.bean.params.oci.securityrule.AddIngressSecurityRuleParams;
import com.yohann.ocihelper.bean.params.oci.securityrule.GetSecurityRuleListPageParams;
import com.yohann.ocihelper.bean.params.oci.securityrule.RemoveSecurityRuleParams;
import com.yohann.ocihelper.bean.response.oci.securityrule.SecurityRuleListRsp;

public interface ISecurityRuleService {

    Page<SecurityRuleListRsp.SecurityRuleInfo> page(GetSecurityRuleListPageParams params);

    void addIngress(AddIngressSecurityRuleParams params);

    void addEgress(AddEgressSecurityRuleParams params);

    void remove(RemoveSecurityRuleParams params);
}
