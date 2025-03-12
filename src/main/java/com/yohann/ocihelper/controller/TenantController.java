package com.yohann.ocihelper.controller;

import com.yohann.ocihelper.bean.ResponseData;
import com.yohann.ocihelper.bean.params.oci.tenant.GetTenantInfoParams;
import com.yohann.ocihelper.bean.response.oci.tenant.TenantInfoRsp;
import com.yohann.ocihelper.service.ITenantService;
import com.yohann.ocihelper.utils.CommonUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

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
    public ResponseData<TenantInfoRsp> tenantInfo(@Validated @RequestBody GetTenantInfoParams params,
                                                  BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        return ResponseData.successData(tenantService.tenantInfo(params));
    }

}
