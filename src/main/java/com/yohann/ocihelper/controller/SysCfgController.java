package com.yohann.ocihelper.controller;

import com.yohann.ocihelper.bean.ResponseData;
import com.yohann.ocihelper.bean.params.sys.*;
import com.yohann.ocihelper.bean.response.sys.GetSysCfgRsp;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.utils.CommonUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.controller
 * @className: SysCfgController
 * @author: Yohann
 * @date: 2024/11/30 17:07
 */
@RestController
@RequestMapping(path = "/api/sys")
public class SysCfgController {

    @Resource
    private ISysService sysService;

    @PostMapping(path = "/login")
    public ResponseData<String> addCfg(@Validated @RequestBody LoginParams params,
                                       BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        return ResponseData.successData(sysService.login(params), "登录成功");
    }

    @PostMapping(path = "/getEnableMfa")
    public ResponseData<Boolean> getEnableMfa() {
        return ResponseData.successData(sysService.getEnableMfa(), "获取系统是否启用MFA成功");
    }

    @PostMapping(path = "/getSysCfg")
    public ResponseData<GetSysCfgRsp> getSysCfg() {
        return ResponseData.successData(sysService.getSysCfg(),"获取系统配置成功");
    }

    @PostMapping(path = "/updateSysCfg")
    public ResponseData<Void> updateSysCfg(@Validated @RequestBody UpdateSysCfgParams params,
                                           BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        sysService.updateSysCfg(params);
        return ResponseData.successData("更新系统配置成功");
    }

    @PostMapping(path = "/sendMsg")
    public ResponseData<Void> sendMsg(@Validated @RequestBody SendMsgParams params,
                                      BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        sysService.sendMessage(params.getMessage());
        return ResponseData.successData("发送消息成功");
    }

    @PostMapping(path = "/backup")
    public void backup(@Validated @RequestBody BackupParams params) {
        sysService.backup(params);
    }

    @PostMapping(path = "/recover")
    public ResponseData<Void> recover(@Validated RecoverParams params,
                                      BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        sysService.recover(params);
        return ResponseData.successData("恢复数据成功");
    }
}
