package com.yohann.ocihelper.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.util.StrUtil;
import com.oracle.bmc.core.VirtualNetworkClient;
import com.oracle.bmc.core.model.*;
import com.oracle.bmc.core.requests.*;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.networkloadbalancer.NetworkLoadBalancerClient;
import com.oracle.bmc.networkloadbalancer.model.*;
import com.oracle.bmc.networkloadbalancer.requests.CreateNetworkLoadBalancerRequest;
import com.oracle.bmc.networkloadbalancer.requests.DeleteNetworkLoadBalancerRequest;
import com.oracle.bmc.networkloadbalancer.requests.GetNetworkLoadBalancerRequest;
import com.oracle.bmc.networkloadbalancer.requests.ListNetworkLoadBalancersRequest;
import com.yohann.ocihelper.bean.Tuple2;
import com.yohann.ocihelper.bean.dto.CreateInstanceDTO;
import com.yohann.ocihelper.bean.dto.InstanceCfgDTO;
import com.yohann.ocihelper.bean.dto.InstanceDetailDTO;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.oci.instance.CreateNetworkLoadBalancerParams;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.enums.ArchitectureEnum;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.IInstanceService;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
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
    @Resource
    private ExecutorService virtualExecutor;

    private static final String LEGACY_MESSAGE_TEMPLATE =
            "【开机任务】 \n\n🎉 用户：[%s] 开机成功 🎉\n" +
                    "时间： %s\n" +
                    "Region： %s\n" +
                    "CPU类型： %s\n" +
                    "CPU： %s\n" +
                    "内存（GB）： %s\n" +
                    "磁盘大小（GB）： %s\n" +
                    "Shape： %s\n" +
                    "公网IP： %s\n" +
                    "root密码： %s";

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
        log.info("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] ，开始执行第 [{}] 次创建实例操作...",
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
                                                           String vnicId,
                                                           SysUserDTO sysUserDTO,
                                                           List<String> cidrList) {
        String publicIp = null;
        String instanceName = null;
        Instance instance = null;
        Tuple2<String, Instance> tuple2;
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            instance = fetcher.getInstanceById(instanceId);
            instanceName = instance.getDisplayName();
            publicIp = fetcher.reassignEphemeralPublicIp(fetcher.getVirtualNetworkClient().getVnic(GetVnicRequest.builder()
                    .vnicId(vnicId)
                    .build()).getVnic());
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
            vcns.parallelStream().forEach(x -> {
                fetcher.releaseSecurityRule(x, 0);
                log.info("用户：[{}] ，区域：[{}] ，放行 vcn： [{}] 安全列表所有端口及协议成功",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), x.getDisplayName());
            });
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

    @Override
    public void oneClick500M(CreateNetworkLoadBalancerParams params) {
        virtualExecutor.execute(() -> {
            SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
            String publicIp = null;
            String instanceName = null;
            try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO);) {
                String compartmentId = fetcher.getCompartmentId();
                VirtualNetworkClient virtualNetworkClient = fetcher.getVirtualNetworkClient();

                // 校验是否为AMD、是否已有实例vnic绑定nat路由表
                String instanceId = params.getInstanceId();
                Instance instance = fetcher.getInstanceById(instanceId);
                instanceName = instance.getDisplayName();
                if (!instance.getShape().contains(ArchitectureEnum.AMD.getShapeDetail())) {
                    log.error("【一键开启500MB任务】实例 Shape: {} 不支持一键开启500MB", instance.getShape());
                    throw new OciException(-1, "该实例不支持一键开启500MB");
                }

                Vcn vcn = fetcher.getVcnByInstanceId(instanceId);
                Vnic vnic = fetcher.getVnicByInstanceId(instanceId);
                String instanceVnicId = vnic.getId();
                String instancePriIp = virtualNetworkClient.listPrivateIps(ListPrivateIpsRequest.builder()
                        .vnicId(vnic.getId())
                        .build()).getItems().getFirst().getIpAddress();

                // NAT网关
                NatGateway natGateway;
                List<NatGateway> natGatewayList = virtualNetworkClient.listNatGateways(ListNatGatewaysRequest.builder()
                        .compartmentId(compartmentId)
                        .lifecycleState(NatGateway.LifecycleState.Available)
                        .vcnId(vcn.getId())
                        .build()).getItems();
                if (CollectionUtil.isNotEmpty(natGatewayList)) {
                    natGateway = natGatewayList.getFirst();
                    log.info("【一键开启500MB任务】获取到已存在的NAT网关：" + natGateway.getDisplayName());
                } else {
                    natGateway = virtualNetworkClient.createNatGateway(CreateNatGatewayRequest.builder()
                            .createNatGatewayDetails(CreateNatGatewayDetails.builder()
                                    .vcnId(vcn.getId())
                                    .compartmentId(compartmentId)
                                    .displayName("nat-gateway")
                                    .build())
                            .build()).getNatGateway();

                    while (!virtualNetworkClient.getNatGateway(GetNatGatewayRequest.builder()
                            .natGatewayId(natGateway.getId())
                            .build()).getNatGateway().getLifecycleState().getValue().equals(NatGateway.LifecycleState.Available.getValue())) {
                        Thread.sleep(1000);
                    }
                    log.info("【一键开启500MB任务】NAT网关创建成功：" + natGateway.getDisplayName());
                }

                // 路由表
                RouteTable routeTable = null;
                List<RouteTable> routeTableList = virtualNetworkClient.listRouteTables(ListRouteTablesRequest.builder()
                        .vcnId(vcn.getId())
                        .compartmentId(compartmentId)
                        .lifecycleState(RouteTable.LifecycleState.Available)
                        .build()).getItems();
                try {
                    if (CollectionUtil.isNotEmpty(routeTableList)) {
                        for (RouteTable table : routeTableList) {
                            for (RouteRule routeRule : table.getRouteRules()) {
                                if (routeRule.getNetworkEntityId().equals(natGateway.getId()) && routeRule.getCidrBlock().equals("0.0.0.0/0")
                                        && routeRule.getDestinationType().getValue().equals(RouteRule.DestinationType.CidrBlock.getValue())) {
                                    routeTable = table;
                                    break;
                                }
                            }
                            if (routeTable != null) {
                                break;
                            }
                        }
                    }
                } catch (Exception e) {

                }

                if (routeTable != null) {
                    for (Instance x : fetcher.listInstances()) {
                        if (x.getShape().contains(ArchitectureEnum.AMD.getShapeDetail())) {
                            Vnic xvnic = fetcher.getVnicByInstanceId(x.getId());
                            if (StrUtil.isNotBlank(xvnic.getRouteTableId()) && xvnic.getRouteTableId().equals(routeTable.getId())
                                    && !x.getId().equals(instanceId)) {
                                throw new OciException(-1, "已有其他免费AMD实例绑定NAT路由表");
                            }
                        }
                    }
                }

                log.warn("【一键开启500MB任务】用户：[{}]，区域：[{}]，实例：[{}] 开始执行一键开启500MB任务...", sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), instance.getDisplayName());

                // 选择一个子网
                Subnet subnet = virtualNetworkClient.listSubnets(ListSubnetsRequest.builder()
                        .compartmentId(compartmentId)
                        .vcnId(vcn.getId())
                        .build()).getItems().getFirst();

                // 网络负载平衡器
                NetworkLoadBalancerClient networkLoadBalancerClient = fetcher.getNetworkLoadBalancerClient();
                List<NetworkLoadBalancerSummary> networkLoadBalancerSummaries = networkLoadBalancerClient.listNetworkLoadBalancers(ListNetworkLoadBalancersRequest.builder()
                        .compartmentId(compartmentId)
                        .lifecycleState(LifecycleState.Active)
                        .build()).getNetworkLoadBalancerCollection().getItems();
                if (CollectionUtil.isNotEmpty(networkLoadBalancerSummaries)) {
                    networkLoadBalancerSummaries.forEach(x -> {
                        log.info("【一键开启500MB任务】正在删除网络负载平衡器：" + x.getDisplayName());
                        networkLoadBalancerClient.deleteNetworkLoadBalancer(DeleteNetworkLoadBalancerRequest.builder()
                                .networkLoadBalancerId(x.getId())
                                .build());
                    });
                }

                log.info("【一键开启500MB任务】开始创建网络负载平衡器...");

                NetworkLoadBalancer networkLoadBalancer = null;
                boolean isNormal = false;
                int retryCount = 0;
                final int MAX_RETRY = 10;

                while (!isNormal) {
                    try {
                        networkLoadBalancer = networkLoadBalancerClient.createNetworkLoadBalancer(CreateNetworkLoadBalancerRequest.builder()
                                .createNetworkLoadBalancerDetails(CreateNetworkLoadBalancerDetails.builder()
                                        .displayName("nlb-" + LocalDateTime.now().format(CommonUtils.DATETIME_FMT_PURE))
                                        .compartmentId(compartmentId)
                                        .isPrivate(false)
                                        .subnetId(subnet.getId())
                                        .listeners(Map.of(
                                                "listener1", ListenerDetails.builder()
                                                        .name("listener1")
                                                        .defaultBackendSetName("backend1")
                                                        .protocol(ListenerProtocols.TcpAndUdp)
                                                        .port(0)
                                                        .build()
                                        ))
                                        .backendSets(Map.of(
                                                "backend1", BackendSetDetails.builder()
                                                        .isPreserveSource(true)
                                                        .isFailOpen(true)
                                                        .policy(NetworkLoadBalancingPolicy.TwoTuple)
                                                        .healthChecker(HealthChecker.builder()
                                                                .protocol(HealthCheckProtocols.Tcp)
                                                                .port(params.getSshPort())
                                                                .build())
                                                        .backends(Collections.singletonList(Backend.builder()
                                                                .targetId(instanceId)
                                                                .ipAddress(instancePriIp)
                                                                .port(0)
                                                                .weight(1)
                                                                .build()))
                                                        .build()
                                        ))
                                        .build())
                                .build()).getNetworkLoadBalancer();

                        isNormal = true;
                    } catch (Exception e) {
                        retryCount++;
                        log.warn("【一键开启500MB任务】第 " + retryCount + " 次创建网络负载平衡器失败，重试中...");
                        if (retryCount >= MAX_RETRY) {
                            log.error("【一键开启500MB任务】创建网络负载平衡器失败次数超过 " + MAX_RETRY + " 次，终止任务。");
                            throw new OciException(-1, "创建网络负载平衡器重试失败次数超过限制", e);
                        }
                        Thread.sleep(30000);
                    }
                }

                while (!networkLoadBalancerClient.getNetworkLoadBalancer(GetNetworkLoadBalancerRequest.builder()
                        .networkLoadBalancerId(networkLoadBalancer.getId())
                        .build()).getNetworkLoadBalancer().getLifecycleState().getValue().equals(LifecycleState.Active.getValue())) {
                    Thread.sleep(1000);
                }

                log.info("【一键开启500MB任务】网络负载平衡器创建成功");
                for (IpAddress x : networkLoadBalancerClient.getNetworkLoadBalancer(GetNetworkLoadBalancerRequest.builder()
                        .networkLoadBalancerId(networkLoadBalancer.getId())
                        .build()).getNetworkLoadBalancer().getIpAddresses()) {
                    if (!CommonUtils.isPrivateIp(x.getIpAddress())) {
                        publicIp = x.getIpAddress();
                        log.info("【一键开启500MB任务】网络负载平衡器公网IP：" + x.getIpAddress());
                    }
                }

                // NAT路由表
                if (routeTable != null) {
                    virtualNetworkClient.updateRouteTable(UpdateRouteTableRequest.builder()
                            .rtId(routeTable.getId())
                            .updateRouteTableDetails(UpdateRouteTableDetails.builder()
                                    .routeRules(Collections.singletonList(RouteRule.builder()
                                            .cidrBlock("0.0.0.0/0")
                                            .networkEntityId(natGateway.getId())
                                            .destinationType(RouteRule.DestinationType.CidrBlock)
                                            .build()))
                                    .build())
                            .build());
                    log.info("【一键开启500MB任务】获取到已存在的NAT路由表：" + routeTable.getDisplayName());
                } else {
                    routeTable = virtualNetworkClient.createRouteTable(CreateRouteTableRequest.builder()
                            .createRouteTableDetails(CreateRouteTableDetails.builder()
                                    .compartmentId(compartmentId)
                                    .vcnId(vcn.getId())
                                    .displayName("nat-route")
                                    .routeRules(Collections.singletonList(RouteRule.builder()
                                            .cidrBlock("0.0.0.0/0")
                                            .networkEntityId(natGateway.getId())
                                            .destinationType(RouteRule.DestinationType.CidrBlock)
                                            .build()))
                                    .build())
                            .build()).getRouteTable();

                    while (!virtualNetworkClient.getRouteTable(GetRouteTableRequest.builder()
                            .rtId(routeTable.getId())
                            .build()).getRouteTable().getLifecycleState().getValue().equals(RouteTable.LifecycleState.Available.getValue())) {
                        Thread.sleep(1000);
                    }

                    log.info("【一键开启500MB任务】NAT路由表创建成功：" + routeTable.getDisplayName());
                }

                // 实例vnic绑定路由表，跳过源/目的地检查
                virtualNetworkClient.updateVnic(UpdateVnicRequest.builder()
                        .vnicId(instanceVnicId)
                        .updateVnicDetails(UpdateVnicDetails.builder()
                                .skipSourceDestCheck(true)
                                .routeTableId(routeTable.getId())
                                .build())
                        .build());

                // 放行所有端口
                fetcher.releaseSecurityRule(vcn, 0);

                log.info("【一键开启500MB任务】实例vnic绑定路由表成功，实例：【{}】已成功开启500MB🎉，公网IP：{}", instance.getDisplayName(), publicIp);
                sysService.sendMessage(String.format("【一键开启500MB任务】用户：[%s]，区域：[%s]，实例：[%s] 已成功开启500MB🎉，公网IP：%s",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), instance.getDisplayName(), publicIp));
            } catch (Exception e) {
                log.error("【一键开启500MB任务】用户：[{}]，区域：[{}]，实例：[{}] 开启500MB失败❌",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), instanceName, e);
                sysService.sendMessage(String.format("【一键开启500MB任务】用户：[%s]，区域：[%s]，实例：[%s] 开启500MB失败❌，错误：%s",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), instanceName, e.getLocalizedMessage()));
            }
        });
    }

}
