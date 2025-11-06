package com.yohann.ocihelper.controller;

import com.yohann.ocihelper.bean.ResponseData;
import com.yohann.ocihelper.bean.params.oci.tenant.GetTenantInfoParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdatePwdExpirationPolicyParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserBasicParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserInfoParams;
import com.yohann.ocihelper.bean.response.oci.tenant.TenantInfoRsp;
import com.yohann.ocihelper.service.ITenantService;
import com.yohann.ocihelper.utils.CommonUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * @ClassName TenantController
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-12 15:51
 **/
@RestController
@RequestMapping(path = "/api/tenant")
public class TenantController {

    @Resource
    private ITenantService tenantService;

    @RequestMapping("tenantInfo")
    public ResponseData<TenantInfoRsp> tenantInfo(@Validated @RequestBody GetTenantInfoParams params) {
        return ResponseData.successData(tenantService.tenantInfo(params));
    }

    @RequestMapping("deleteUser")
    public ResponseData<Void> deleteUser(@Validated @RequestBody UpdateUserBasicParams params) {
        tenantService.deleteUser(params);
        return ResponseData.successData("删除用户成功");
    }

    @RequestMapping("deleteMfaDevice")
    public ResponseData<Void> deleteMfaDevice(@Validated @RequestBody UpdateUserBasicParams params) {
        tenantService.deleteMfaDevice(params);
        return ResponseData.successData("清除 MFA 设备成功");
    }

    @RequestMapping("deleteApiKey")
    public ResponseData<Void> deleteApiKey(@Validated @RequestBody UpdateUserBasicParams params) {
        tenantService.deleteApiKey(params);
        return ResponseData.successData("清除所有 API 成功");
    }

    @RequestMapping("resetPassword")
    public ResponseData<Void> resetPassword(@Validated @RequestBody UpdateUserBasicParams params) {
        tenantService.resetPassword(params);
        return ResponseData.successData("重置用户密码成功");
    }

    @RequestMapping("updateUserInfo")
    public ResponseData<Void> updateUserInfo(@Validated @RequestBody UpdateUserInfoParams params) {
        tenantService.updateUserInfo(params);
        return ResponseData.successData("更新用户信息成功");
    }

    @RequestMapping("updatePwdEx")
    public ResponseData<Void> updatePwdEx(@Validated @RequestBody UpdatePwdExpirationPolicyParams params) {
        tenantService.updatePwdExpirationPolicy(params);
        return ResponseData.successData("更新密码过期时间成功");
    }

}
