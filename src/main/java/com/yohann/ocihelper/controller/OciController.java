package com.yohann.ocihelper.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yohann.ocihelper.bean.ResponseData;
import com.yohann.ocihelper.bean.dto.InstanceCfgDTO;
import com.yohann.ocihelper.bean.params.*;
import com.yohann.ocihelper.bean.params.oci.cfg.*;
import com.yohann.ocihelper.bean.params.oci.instance.*;
import com.yohann.ocihelper.bean.params.oci.securityrule.ReleaseSecurityRuleParams;
import com.yohann.ocihelper.bean.params.oci.task.CreateTaskPageParams;
import com.yohann.ocihelper.bean.params.oci.task.StopChangeIpParams;
import com.yohann.ocihelper.bean.params.oci.task.StopCreateParams;
import com.yohann.ocihelper.bean.params.oci.volume.UpdateBootVolumeCfgParams;
import com.yohann.ocihelper.bean.response.oci.task.CreateTaskRsp;
import com.yohann.ocihelper.bean.response.oci.cfg.OciCfgDetailsRsp;
import com.yohann.ocihelper.bean.response.oci.cfg.OciUserListRsp;
import com.yohann.ocihelper.service.IOciService;
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
 * OciController
 * </p >
 *
 * @author yohann
 * @since 2024/11/12 17:17
 */
@RestController
@RequestMapping(path = "/api/oci")
public class OciController {

    @Resource
    private IOciService ociService;

    @PostMapping(path = "/userPage")
    public ResponseData<Page<OciUserListRsp>> userPage(@Validated @RequestBody GetOciUserListParams params,
                                                       BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        return ResponseData.successData(ociService.userPage(params), "获取用户分页成功");
    }

    @PostMapping(path = "/addCfg")
    public ResponseData<Void> addCfg(@Validated @RequestBody AddCfgParams params,
                                     BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.addCfg(params);
        return ResponseData.successData("新增配置成功");
    }

    @PostMapping(path = "/uploadCfg")
    public ResponseData<Void> uploadCfg(@Validated UploadCfgParams params,
                                        BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.uploadCfg(params);
        return ResponseData.successData("上传配置成功");
    }

    @PostMapping(path = "/removeCfg")
    public ResponseData<Void> removeCfg(@Validated @RequestBody IdListParams params,
                                        BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.removeCfg(params);
        return ResponseData.successData("删除配置成功");
    }

    @PostMapping(path = "/createInstance")
    public ResponseData<Void> createInstance(@Validated @RequestBody CreateInstanceParams params,
                                             BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.createInstance(params);
        return ResponseData.successData("创建开机任务成功");
    }

    @PostMapping(path = "/details")
    public ResponseData<OciCfgDetailsRsp> details(@Validated @RequestBody GetOciCfgDetailsParams params,
                                                  BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        return ResponseData.successData(ociService.details(params), "获取配置详情成功");
    }

    @PostMapping(path = "/changeIp")
    public ResponseData<Void> changeIp(@Validated @RequestBody ChangeIpParams params,
                                       BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.changeIp(params);
        return ResponseData.successData("创建实例更换IP任务成功");
    }

    @PostMapping(path = "/stopCreate")
    public ResponseData<Void> stopCreate(@Validated @RequestBody StopCreateParams params,
                                         BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.stopCreate(params);
        return ResponseData.successData("停止开机任务成功");
    }

    @PostMapping(path = "/stopChangeIp")
    public ResponseData<Void> stopChangeIp(@Validated @RequestBody StopChangeIpParams params,
                                           BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.stopChangeIp(params);
        return ResponseData.successData("停止更换IP任务成功");
    }

    @PostMapping(path = "/createTaskPage")
    public ResponseData<Page<CreateTaskRsp>> createTaskPage(@Validated @RequestBody CreateTaskPageParams params,
                                                            BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        return ResponseData.successData(ociService.createTaskPage(params), "获取开机任务列表成功");
    }

    @PostMapping(path = "/stopCreateBatch")
    public ResponseData<Void> stopCreateBatch(@Validated @RequestBody IdListParams params,
                                              BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.stopCreateBatch(params);
        return ResponseData.successData("停止开机任务成功");
    }

    @PostMapping(path = "/createInstanceBatch")
    public ResponseData<Void> createInstanceBatch(@Validated @RequestBody CreateInstanceBatchParams params,
                                                  BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.createInstanceBatch(params);
        return ResponseData.successData("批量创建开机任务成功");
    }

    @PostMapping(path = "/updateInstanceState")
    public ResponseData<Void> updateInstanceState(@Validated @RequestBody UpdateInstanceStateParams params,
                                                  BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.updateInstanceState(params);
        return ResponseData.successData("更新实例状态成功");
    }

    @PostMapping(path = "/sendCaptcha")
    public ResponseData<Void> sendCaptcha(@Validated @RequestBody SendCaptchaParams params,
                                          BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.sendCaptcha(params);
        return ResponseData.successData("验证码已发送，请查看TG或钉钉消息");
    }

    @PostMapping(path = "/terminateInstance")
    public ResponseData<Void> terminateInstance(@Validated @RequestBody TerminateInstanceParams params,
                                                BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.terminateInstance(params);
        return ResponseData.successData("终止实例命令已下发");
    }

    @PostMapping(path = "/releaseSecurityRule")
    public ResponseData<Void> releaseSecurityRule(@Validated @RequestBody ReleaseSecurityRuleParams params,
                                                  BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.releaseSecurityRule(params);
        return ResponseData.successData("安全列表放行成功");
    }

    @PostMapping(path = "/getInstanceCfgInfo")
    public ResponseData<InstanceCfgDTO> getInstanceCfgInfo(@Validated @RequestBody GetInstanceCfgInfoParams params,
                                                           BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        return ResponseData.successData(ociService.getInstanceCfgInfo(params), "获取实例配置信息成功");
    }

    @PostMapping(path = "/createIpv6")
    public ResponseData<Void> createIpv6(@Validated @RequestBody CreateIpv6Params params,
                                         BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.createIpv6(params);
        return ResponseData.successData("为实例附加 IPV6 成功");
    }

    @PostMapping(path = "/updateInstanceName")
    public ResponseData<Void> updateInstanceName(@Validated @RequestBody UpdateInstanceNameParams params,
                                         BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.updateInstanceName(params);
        return ResponseData.successData("修改实例名称成功");
    }

    @PostMapping(path = "/updateInstanceCfg")
    public ResponseData<Void> updateInstanceCfg(@Validated @RequestBody UpdateInstanceCfgParams params,
                                         BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.updateInstanceCfg(params);
        return ResponseData.successData("修改实例配置成功");
    }

    @PostMapping(path = "/updateBootVolumeCfg")
    public ResponseData<Void> updateBootVolumeCfg(@Validated @RequestBody UpdateBootVolumeCfgParams params,
                                         BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        ociService.updateBootVolumeCfg(params);
        return ResponseData.successData("修改引导卷配置成功");
    }

    @PostMapping(path = "/checkAlive")
    public ResponseData<Void> checkAlive() {
        return ResponseData.successData(ociService.checkAlive());
    }
}
