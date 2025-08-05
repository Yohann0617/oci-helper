package com.yohann.ocihelper.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yohann.ocihelper.bean.ResponseData;
import com.yohann.ocihelper.bean.params.ipdata.*;
import com.yohann.ocihelper.bean.response.ipdata.IpDataPageRsp;
import com.yohann.ocihelper.service.IIpDataService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.controller
 * @className: IpDataController
 * @author: Yohann
 * @date: 2025/8/5 21:53
 */
@RestController
@RequestMapping(path = "/api/ipData")
public class IpDataController {

    @Resource
    private IIpDataService ipDataService;

    @PostMapping("/add")
    public ResponseData<Void> add(@Validated @RequestBody AddIpDataParams params){
        ipDataService.add(params);
        return ResponseData.successData();
    }

    @PostMapping("/update")
    public ResponseData<Void> update(@Validated @RequestBody UpdateIpDataParams params){
        ipDataService.updateIpData(params);
        return ResponseData.successData();
    }

    @PostMapping("/remove")
    public ResponseData<Void> remove(@Validated @RequestBody RemoveIpDataParams params){
        ipDataService.removeIpData(params);
        return ResponseData.successData();
    }

    @PostMapping("/loadOciIpData")
    public ResponseData<Void> loadOciIpData(){
        ipDataService.loadOciIpData();
        return ResponseData.successData();
    }

    @PostMapping("/page")
    public ResponseData<Page<IpDataPageRsp>> page(@Validated @RequestBody PageIpDataParams params){
        return ResponseData.successData(ipDataService.pageIpData(params));
    }
}
