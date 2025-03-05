package com.yohann.ocihelper.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yohann.ocihelper.bean.ResponseData;
import com.yohann.ocihelper.bean.params.oci.securityrule.AddEgressSecurityRuleParams;
import com.yohann.ocihelper.bean.params.oci.securityrule.AddIngressSecurityRuleParams;
import com.yohann.ocihelper.bean.params.oci.securityrule.GetSecurityRuleListPageParams;
import com.yohann.ocihelper.bean.params.oci.securityrule.RemoveSecurityRuleParams;
import com.yohann.ocihelper.bean.response.oci.securityrule.SecurityRuleListRsp;
import com.yohann.ocihelper.service.ISecurityRuleService;
import com.yohann.ocihelper.utils.CommonUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 * SecurityRuleController
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/12/31 15:48
 */
@RestController
@RequestMapping(path = "/api/securityRule")
public class SecurityRuleController {

    @Resource
    private ISecurityRuleService securityRuleService;

    @RequestMapping("/page")
    public ResponseData<Page<SecurityRuleListRsp.SecurityRuleInfo>> page(@Validated @RequestBody GetSecurityRuleListPageParams params,
                                                                         BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        return ResponseData.successData(securityRuleService.page(params));
    }

    @RequestMapping("/addIngress")
    public ResponseData<Void> addIngress(@Validated @RequestBody AddIngressSecurityRuleParams params,
                                         BindingResult bindingResult){
        CommonUtils.checkAndThrow(bindingResult);
        securityRuleService.addIngress(params);
        return ResponseData.successData();
    }

    @RequestMapping("/addEgress")
    public ResponseData<Void> addEgress(@Validated @RequestBody AddEgressSecurityRuleParams params,
                                         BindingResult bindingResult){
        CommonUtils.checkAndThrow(bindingResult);
        securityRuleService.addEgress(params);
        return ResponseData.successData();
    }

    @RequestMapping("/remove")
    public ResponseData<Void> remove(@Validated @RequestBody RemoveSecurityRuleParams params,
                                         BindingResult bindingResult){
        CommonUtils.checkAndThrow(bindingResult);
        securityRuleService.remove(params);
        return ResponseData.successData();
    }
}
