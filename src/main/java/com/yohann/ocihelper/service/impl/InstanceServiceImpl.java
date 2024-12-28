package com.yohann.ocihelper.service.impl;

import cn.hutool.core.date.DatePattern;
import com.oracle.bmc.core.model.*;
import com.oracle.bmc.model.BmcException;
import com.yohann.ocihelper.bean.Tuple2;
import com.yohann.ocihelper.bean.dto.CreateInstanceDTO;
import com.yohann.ocihelper.bean.dto.InstanceCfgDTO;
import com.yohann.ocihelper.bean.dto.InstanceDetailDTO;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.response.oci.OciCfgDetailsRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.IInstanceService;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.yohann.ocihelper.service.impl.OciServiceImpl.TEMP_MAP;

/**
 * <p>
 * InstanceServiceImpl
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/11 14:30
 */
@Slf4j
@Service
public class InstanceServiceImpl implements IInstanceService {

    @Resource
    private ISysService sysService;

    private static final String LEGACY_MESSAGE_TEMPLATE =
            "【开机任务】 🎉 用户：[%s] 开机成功 🎉\n\n" +
                    "时间： %s\n" +
                    "Region： %s\n" +
                    "CPU类型： %s\n" +
                    "CPU： %s\n" +
                    "内存（GB）： %s\n" +
                    "磁盘大小（GB）： %s\n" +
                    "Shape： %s\n" +
                    "公网IP： %s\n" +
                    "root密码： %s\n\n" +
                    "⭐注意： 如果没有开机任务请及时清理API";

    @Override
    public List<SysUserDTO.CloudInstance> listRunningInstances(SysUserDTO sysUserDTO) {
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            return fetcher.listInstances().parallelStream()
                    .map(x -> SysUserDTO.CloudInstance.builder()
                            .region(x.getRegion())
                            .name(x.getDisplayName())
                            .ocId(x.getId())
                            .shape(x.getShape())
                            .publicIp(fetcher.listInstanceIPs(x.getId()).stream().map(Vnic::getPublicIp).collect(Collectors.toList()))
                            .build())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new OciException(-1, "获取实例信息失败");
        }

    }

    @Override
    public CreateInstanceDTO createInstance(OracleInstanceFetcher fetcher) {
        Long currentCount = (Long) TEMP_MAP.compute(
                CommonUtils.CREATE_COUNTS_PREFIX + fetcher.getUser().getTaskId(),
                (key, value) -> value == null ? 1L : Long.parseLong(String.valueOf(value)) + 1
        );
        log.info("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] ，开始执行第 [{}] 次创建实例操作......",
                fetcher.getUser().getUsername(), fetcher.getUser().getOciCfg().getRegion(),
                fetcher.getUser().getArchitecture(), fetcher.getUser().getCreateNumbers(), currentCount);

        List<InstanceDetailDTO> instanceList = new ArrayList<>();
        for (int i = 0; i < fetcher.getUser().getCreateNumbers(); i++) {
            InstanceDetailDTO instanceDetail = fetcher.createInstanceData();
            if (instanceDetail.isTooManyReq()) {
                log.info("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] ，执行第 [{}] 次创建实例操作时创建第 [{}] 台时请求频繁，本次任务暂停",
                        fetcher.getUser().getUsername(), fetcher.getUser().getOciCfg().getRegion(),
                        fetcher.getUser().getArchitecture(), fetcher.getUser().getCreateNumbers(), currentCount, i + 1
                );
                break;
            }
            instanceList.add(instanceDetail);

            if (instanceDetail.isSuccess()) {
                log.info("---------------- 🎉 用户：{} 开机成功，CPU类型：{}，公网IP：{}，root密码：{} 🎉 ----------------",
                        instanceDetail.getUsername(), instanceDetail.getArchitecture(),
                        instanceDetail.getPublicIp(), instanceDetail.getRootPassword());
                String message = String.format(LEGACY_MESSAGE_TEMPLATE,
                        instanceDetail.getUsername(),
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)),
                        instanceDetail.getRegion(),
                        instanceDetail.getArchitecture(),
                        instanceDetail.getOcpus(),
                        instanceDetail.getMemory(),
                        instanceDetail.getDisk(),
                        instanceDetail.getShape(),
                        instanceDetail.getPublicIp(),
                        instanceDetail.getRootPassword());

                sysService.sendMessage(message);
            }
        }

        return new CreateInstanceDTO(instanceList);
    }

    @Override
    public Tuple2<String, Instance> changeInstancePublicIp(String instanceId,
                                                           SysUserDTO sysUserDTO,
                                                           List<String> cidrList) {
        String publicIp = null;
        String instanceName = null;
        Instance instance = null;
        Tuple2<String, Instance> tuple2;
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            instance = fetcher.getInstanceById(instanceId);
            instanceName = instance.getDisplayName();
            publicIp = fetcher.reassignEphemeralPublicIp(fetcher.listInstanceIPs(instance.getId()).get(0));
            tuple2 = Tuple2.of(publicIp, instance);
            return tuple2;
        } catch (BmcException ociException) {
            log.error("【更换公共IP】用户：[{}] ，区域：[{}] ，实例：[{}] ，更换公共IP失败，原因：{}",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), instanceName,
                    ociException.getLocalizedMessage());
            tuple2 = Tuple2.of(publicIp, instance);
        } catch (Exception e) {
            log.error("【更换公共IP】用户：[{}] ，区域：[{}] ，实例：[{}] ，执行更换IP任务异常：{}",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), instanceName,
                    e.getLocalizedMessage());
            tuple2 = Tuple2.of(publicIp, instance);
        }
        return tuple2;
    }

    @Override
    public InstanceCfgDTO getInstanceCfgInfo(SysUserDTO sysUserDTO, String instanceId) {
        String instanceName = null;
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            Instance instance = fetcher.getInstanceById(instanceId);
            instanceName = instance.getDisplayName();
            return fetcher.getInstanceCfg(instanceId);
        } catch (Exception e) {
            log.error("用户：[{}] ，区域：[{}] ，实例：[{}] 获取实例配置信息失败，原因：{}",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                    instanceName, e.getLocalizedMessage(), e);
            throw new OciException(-1, "获取实例配置信息失败");
        }
    }

    @Override
    public void releaseSecurityRule(SysUserDTO sysUserDTO) {
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            List<Vcn> vcns = fetcher.listVcn();
            if (null == vcns || vcns.isEmpty()) {
                throw new OciException(-1, "当前用户未创建VCN，无法放行安全列表");
            }
            fetcher.releaseSecurityRule(vcns.get(0), 0);
            log.info("用户：[{}] ，区域：[{}] ，放行安全列表所有端口及协议成功",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion());
        } catch (Exception e) {
            log.error("用户：[{}] ，区域：[{}] ，放行安全列表所有端口及协议，原因：{}",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                    e.getLocalizedMessage(), e);
            throw new OciException(-1, "放行安全列表所有端口及协议失败");
        }
    }

    @Override
    public String createIpv6(SysUserDTO sysUserDTO, String instanceId) {
        String instanceName = null;
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            Vcn vcn = fetcher.getVcnByInstanceId(instanceId);
            Instance instance = fetcher.getInstanceById(instanceId);
            Vnic vnic = fetcher.getVnicByInstanceId(instanceId);
            Ipv6 ipv6 = fetcher.createIpv6(vnic, vcn);
            instanceName = instance.getDisplayName();
            log.info("用户：[{}] ，区域：[{}] ，实例：[{}] 附加 IPV6 成功，IPV6地址：{}",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                    instanceName, ipv6.getIpAddress());
            return ipv6.getIpAddress();
        } catch (Exception e) {
            log.error("用户：[{}] ，区域：[{}] ，实例：[{}] 附加 IPV6 失败，原因：{}",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                    instanceName, e.getLocalizedMessage(), e);
            throw new OciException(-1, "附加 IPV6 失败");
        }
    }

    @Override
    public void updateInstanceName(SysUserDTO sysUserDTO, String instanceId, String name) {
        String instanceName = null;
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            instanceName = fetcher.getInstanceById(instanceId).getDisplayName();
            fetcher.updateInstanceName(instanceId, name);
            log.info("用户：[{}] ，区域：[{}] ，实例：[{}] 修改名称成功",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), instanceName);
        } catch (Exception e) {
            log.error("用户：[{}] ，区域：[{}] ，实例：[{}] 修改名称失败，原因：{}",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                    instanceName, e.getLocalizedMessage(), e);
            throw new OciException(-1, "修改实例名称失败");
        }
    }

    @Override
    public void updateInstanceCfg(SysUserDTO sysUserDTO, String instanceId, float ocpus, float memory) {
        String instanceName = null;
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            instanceName = fetcher.getInstanceById(instanceId).getDisplayName();
            fetcher.updateInstanceCfg(instanceId, ocpus, memory);
            log.info("用户：[{}] ，区域：[{}] ，实例：[{}] 修改实例配置成功",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), instanceName);
        } catch (Exception e) {
            log.error("用户：[{}] ，区域：[{}] ，实例：[{}] 修改实例配置失败，原因：{}",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                    instanceName, e.getLocalizedMessage(), e);
            throw new OciException(-1, "修改实例配置失败");
        }
    }

    @Override
    public void updateBootVolumeCfg(SysUserDTO sysUserDTO, String instanceId, long size, long vpusPer) {
        String bootVolumeName = null;
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            BootVolume bootVolume = fetcher.getBootVolumeByInstanceId(instanceId);
            bootVolumeName = bootVolume.getDisplayName();
            fetcher.updateBootVolumeCfg(bootVolume.getId(), size, vpusPer);
            log.info("用户：[{}] ，区域：[{}] ，引导卷：[{}] 修改引导卷配置成功",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), bootVolumeName);
        } catch (Exception e) {
            log.error("用户：[{}] ，区域：[{}] ，引导卷：[{}] 修改引导卷配置失败，原因：{}",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                    bootVolumeName, e.getLocalizedMessage(), e);
            throw new OciException(-1, "修改引导卷配置失败");
        }
    }

}
