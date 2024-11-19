package com.yohann.ocihelper.service;

import com.oracle.bmc.core.model.Instance;
import com.yohann.ocihelper.bean.Tuple2;
import com.yohann.ocihelper.bean.dto.CreateInstanceDTO;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
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
     * @param fetcher oci配置
     * @return 已开机实例信息
     */
    List<SysUserDTO.CloudInstance> listRunningInstances(OracleInstanceFetcher fetcher);

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
     * @param sysUserDTO oci配置
     * @param cidrList   CIDR 网段 （传为空则随机更换一个ip）
     * @return 新的实例公共IP，实例
     */
    Tuple2<String, Instance> changeInstancePublicIp(String instanceId, SysUserDTO sysUserDTO, List<String> cidrList);
}
