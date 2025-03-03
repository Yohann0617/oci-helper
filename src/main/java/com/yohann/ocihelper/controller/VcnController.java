package com.yohann.ocihelper.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yohann.ocihelper.bean.ResponseData;
import com.yohann.ocihelper.bean.params.oci.vcn.RemoveVcnParams;
import com.yohann.ocihelper.bean.params.oci.vcn.VcnPageParams;
import com.yohann.ocihelper.bean.response.oci.vcn.VcnPageRsp;
import com.yohann.ocihelper.service.IVcnService;
import com.yohann.ocihelper.utils.CommonUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.controller
 * @className: VcnController
 * @author: Yohann
 * @date: 2025/3/3 20:56
 */
@RestController
@RequestMapping(path = "/api/vcn")
public class VcnController {

    @Resource
    private IVcnService vcnService;

    @RequestMapping(path = "page")
    public ResponseData<Page<VcnPageRsp.VcnInfo>> page(@Validated @RequestBody VcnPageParams params,
                                                       BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        return ResponseData.successData(vcnService.page(params));
    }

    @RequestMapping(path = "/remove")
    public ResponseData<Void> remove(@Validated @RequestBody RemoveVcnParams params,
                                     BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        vcnService.remove(params);
        return ResponseData.successData("删除vcn成功");
    }
}
