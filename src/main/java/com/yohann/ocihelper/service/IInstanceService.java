package com.yohann.ocihelper.service;

import com.oracle.bmc.core.model.Instance;
import com.yohann.ocihelper.bean.dto.InstanceDetailDTO;
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
    InstanceDetailDTO createInstance(OracleInstanceFetcher fetcher);

    /**
     * 根据 CIDR 网段更换实例公共IP
     *
     * @param fetcher  oci配置
     * @param instance 实例
     * @param cidrList CIDR 网段 （传为空则随机更换一个ip）
     * @return 新的实例公共IP
     */
    String changeInstancePublicIp(OracleInstanceFetcher fetcher, Instance instance, List<String> cidrList);
}
