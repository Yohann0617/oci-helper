package com.yohann.ocihelper.service;

import com.oracle.bmc.core.model.Instance;
import com.yohann.ocihelper.bean.Tuple2;
import com.yohann.ocihelper.bean.dto.CreateInstanceDTO;
import com.yohann.ocihelper.bean.dto.InstanceCfgDTO;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.oci.instance.Close500MParams;
import com.yohann.ocihelper.bean.params.oci.instance.CreateNetworkLoadBalancerParams;
import com.yohann.ocihelper.bean.params.oci.instance.UpdateShapeParams;
import com.yohann.ocihelper.config.OracleInstanceFetcher;

import java.util.List;

/**
 * <p>
 * IInstanceService
 * </p >
 *
 * @author yohann
 * @since 2024/11/11 14:30
 */
public interface IInstanceService {

    /**
     * 获取已开机实例信息
     *
     * @param sysUserDTO oci配置
     * @return 已开机实例信息
     */
    List<SysUserDTO.CloudInstance> listRunningInstances(SysUserDTO sysUserDTO);

    /**
     * 开机
     *
     * @param fetcher oci配置
     * @return 成功开机的实例信息
     */
    CreateInstanceDTO createInstance(OracleInstanceFetcher fetcher);

    /**
     * 根据 CIDR 网段更换实例公共IP
     *
     * @param instanceId 实例Id
     * @param vnicId     vnicId
     * @param sysUserDTO oci配置
     * @param cidrList   CIDR 网段 （传为空则随机更换一个ip）
     * @return 新的实例公共IP，实例
     */
    Tuple2<String, Instance> changeInstancePublicIp(String instanceId, String vnicId, SysUserDTO sysUserDTO, List<String> cidrList);

    /**
     * 获取实例需修改的配置信息
     *
     * @param sysUserDTO oci配置
     * @param instanceId 实例Id
     * @return 实例需修改的配置信息
     */
    InstanceCfgDTO getInstanceCfgInfo(SysUserDTO sysUserDTO, String instanceId);

    /**
     * 安全列表放行
     *
     * @param sysUserDTO oci配置
     */
    void releaseSecurityRule(SysUserDTO sysUserDTO);

    /**
     * 附加IPV6
     *
     * @param sysUserDTO oci配置
     * @param instanceId 实例Id
     */
    String createIpv6(SysUserDTO sysUserDTO, String instanceId);

    /**
     * 修改实例名称
     *
     * @param sysUserDTO oci配置
     * @param instanceId 实例Id
     * @param name       实例名称
     */
    void updateInstanceName(SysUserDTO sysUserDTO, String instanceId, String name);

    /**
     * 修改实例配置
     *
     * @param sysUserDTO oci配置
     * @param instanceId 实例Id
     * @param ocpus      cpu
     * @param memory     内存
     */
    void updateInstanceCfg(SysUserDTO sysUserDTO, String instanceId, float ocpus, float memory);

    /**
     * 修改引导卷配置
     *
     * @param sysUserDTO oci配置
     * @param instanceId 实例id
     * @param size       引导卷大小
     * @param vpusPer    引导卷vpu [10,120]
     */
    void updateBootVolumeCfg(SysUserDTO sysUserDTO, String instanceId, long size, long vpusPer);

    /**
     * 一键开启500M
     * @param params 参数
     */
    void oneClick500M(CreateNetworkLoadBalancerParams params);

    /**
     * 一键关闭500M
     * @param params 参数
     */
    void oneClickClose500M(Close500MParams params);

    /**
     * 修改实例Shape
     * @param params 参数
     */
    void updateInstanceShape(UpdateShapeParams params);
}
