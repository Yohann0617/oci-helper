package com.yohann.ocihelper.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

/**
 * <p>
 * IOciService
 * </p >
 *
 * @author yohann
 * @since 2024/11/12 11:15
 */
public interface IOciService {

    Page<OciUserListRsp> userPage(GetOciUserListParams params);

    void addCfg(AddCfgParams params);

    void removeCfg(IdListParams params);

    void createInstance(CreateInstanceParams params);

    OciCfgDetailsRsp details(GetOciCfgDetailsParams params);

    void changeIp(ChangeIpParams params);

    void stopCreate(StopCreateParams params);

    void stopChangeIp(StopChangeIpParams params);

    Page<CreateTaskRsp> createTaskPage(CreateTaskPageParams params);

    void stopCreateBatch(IdListParams params);

    void createInstanceBatch(CreateInstanceBatchParams params);

    void uploadCfg(UploadCfgParams params);

    void updateInstanceState(UpdateInstanceStateParams params);

    void terminateInstance(TerminateInstanceParams params);

    void sendCaptcha(SendCaptchaParams params);

    void releaseSecurityRule(ReleaseSecurityRuleParams params);

    InstanceCfgDTO getInstanceCfgInfo(GetInstanceCfgInfoParams params);

    void createIpv6(CreateIpv6Params params);

    void updateInstanceName(UpdateInstanceNameParams params);

    void updateInstanceCfg(UpdateInstanceCfgParams params);

    void updateBootVolumeCfg(UpdateBootVolumeCfgParams params);

    String checkAlive();
}
