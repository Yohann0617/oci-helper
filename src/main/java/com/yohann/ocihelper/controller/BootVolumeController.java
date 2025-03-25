package com.yohann.ocihelper.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yohann.ocihelper.bean.ResponseData;
import com.yohann.ocihelper.bean.params.oci.volume.BootVolumePageParams;
import com.yohann.ocihelper.bean.params.oci.volume.TerminateBootVolumeParams;
import com.yohann.ocihelper.bean.params.oci.volume.UpdateBootVolumeParams;
import com.yohann.ocihelper.bean.response.oci.volume.BootVolumeListPage;
import com.yohann.ocihelper.service.IBootVolumeService;
import com.yohann.ocihelper.utils.CommonUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 * BootVolumeController
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/12/31 15:46
 */
@RestController
@RequestMapping(path = "/api/bootVolume")
public class BootVolumeController {

    @Resource
    private IBootVolumeService bootVolumeService;

    @PostMapping(path = "/page")
    public ResponseData<Page<BootVolumeListPage.BootVolumeInfo>> userPage(@Validated @RequestBody BootVolumePageParams params) {
        return ResponseData.successData(bootVolumeService.bootVolumeListPage(params), "获取引导卷分页列表成功");
    }

    @PostMapping(path = "/terminate")
    public ResponseData<Void> terminate(@Validated @RequestBody TerminateBootVolumeParams params) {
        bootVolumeService.terminateBootVolume(params);
        return ResponseData.successData("终止引导卷命令下发成功");
    }

    @PostMapping(path = "/update")
    public ResponseData<Void> update(@Validated @RequestBody UpdateBootVolumeParams params) {
        bootVolumeService.update(params);
        return ResponseData.successData("更改引导卷配置成功");
    }
}
