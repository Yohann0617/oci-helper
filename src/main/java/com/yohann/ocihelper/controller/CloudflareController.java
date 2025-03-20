package com.yohann.ocihelper.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yohann.ocihelper.bean.ResponseData;
import com.yohann.ocihelper.bean.params.IdListParams;
import com.yohann.ocihelper.bean.params.cf.AddCfCfgParams;
import com.yohann.ocihelper.bean.params.cf.ListCfCfgParams;
import com.yohann.ocihelper.bean.params.cf.UpdateCfCfgParams;
import com.yohann.ocihelper.bean.response.cf.ListCfCfgPageRsp;
import com.yohann.ocihelper.service.ICfCfgService;
import com.yohann.ocihelper.utils.CommonUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @ClassName CloudflareController
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-20 13:43
 **/
@RestController
@RequestMapping(path = "/api/cf")
public class CloudflareController {

    @Resource
    private ICfCfgService cfCfgService;

    @RequestMapping("/listCfg")
    public ResponseData<Page<ListCfCfgPageRsp>> listCfg(@Validated @RequestBody ListCfCfgParams params,
                                                        BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        return ResponseData.successData(cfCfgService.listCfg(params));
    }

    @RequestMapping("/add")
    public ResponseData<Void> addCfCfg(@Validated @RequestBody AddCfCfgParams params,
                                       BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        cfCfgService.addCfCfg(params);
        return ResponseData.successData();
    }

    @RequestMapping("/removeBatch")
    public ResponseData<Void> removeCfCfg(@Validated @RequestBody IdListParams params,
                                       BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        cfCfgService.removeCfCfg(params);
        return ResponseData.successData();
    }

    @RequestMapping("/update")
    public ResponseData<Void> updateCfCfg(@Validated @RequestBody UpdateCfCfgParams params,
                                       BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        cfCfgService.updateCfCfg(params);
        return ResponseData.successData();
    }
}
