package com.yohann.ocihelper;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.VirtualNetworkClient;
import com.oracle.bmc.core.model.*;
import com.oracle.bmc.core.requests.*;
import com.oracle.bmc.core.responses.*;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.model.AvailabilityDomain;
import com.oracle.bmc.networkloadbalancer.NetworkLoadBalancerClient;
import com.oracle.bmc.networkloadbalancer.model.*;
import com.oracle.bmc.networkloadbalancer.requests.*;
import com.yohann.ocihelper.bean.dto.ConsoleConnectionResultDTO;
import com.yohann.ocihelper.bean.dto.InstanceDetailDTO;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.enums.InstanceActionEnum;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.IInstanceService;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import com.yohann.ocihelper.utils.OciConsoleUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import jakarta.annotation.Resource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@MockBean(ServerEndpointExporter.class) // Mock 掉，不让它真正注册
class OciHelperApplicationTests {

    @Resource
    private IInstanceService instanceService;
    @Resource
    private CustomExpiryGuavaCache<String, Object> customCache;

    @Test
    void contextLoads() throws IOException {
        String baseDir = "C:\\Users\\yohann_fan\\Desktop\\test\\oci-helper\\";
//        String baseDir = "C:\\Users\\Yohann\\Desktop\\";
        String s = FileUtil.readString(baseDir + "us-mine.txt", Charset.defaultCharset());
        List<OciUser> ociUsers = CommonUtils.parseConfigContent(s);
        OciUser ociUser = ociUsers.get(0);

//        System.out.println(ociUser);

//        String instanceId = "ocid1.instance.oc1.sa-saopaulo-1.xxx";

        SysUserDTO sysUserDTO = SysUserDTO.builder()
                .ociCfg(SysUserDTO.OciCfg.builder()
                        .userId(ociUser.getOciUserId())
                        .tenantId(ociUser.getOciTenantId())
                        .region(ociUser.getOciRegion())
                        .fingerprint(ociUser.getOciFingerprint())
                        .privateKeyPath(baseDir + ociUser.getOciKeyPath())
                        .build())
                .username(ociUser.getUsername())
                .build();

        System.out.println(sysUserDTO);

        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO);) {
            IdentityClient identityClient = fetcher.getIdentityClient();
            String compartmentId = fetcher.getCompartmentId();
            ComputeClient computeClient = fetcher.getComputeClient();
            VirtualNetworkClient virtualNetworkClient = fetcher.getVirtualNetworkClient();

            // todo 需要传SSH端口
            int sshPort = 22;

            // todo 校验是否为AMD、是否已有实例vnic绑定nat路由表

            // todo 实例
            String instanceId = "ocid1.instance.oc1.us-sanjose-1.xxx";

            Vcn vcn = fetcher.getVcnByInstanceId(instanceId);
            Vnic vnic = fetcher.getVnicByInstanceId(instanceId);
            String instanceVnicId = vnic.getId();
            String instancePriIp = virtualNetworkClient.listPrivateIps(ListPrivateIpsRequest.builder()
                    .vnicId(vnic.getId())
                    .build()).getItems().getFirst().getIpAddress();

            // todo 放行所有端口
//            fetcher.releaseSecurityRule(vcn,0);

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
                    System.out.println("正在删除网络负载平衡器：" + x.getDisplayName());
                    networkLoadBalancerClient.deleteNetworkLoadBalancer(DeleteNetworkLoadBalancerRequest.builder()
                            .networkLoadBalancerId(x.getId())
                            .build());
                });
            }

            System.out.println("开始创建网络负载平衡器...");

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
                                                            .port(sshPort)
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

                    // 成功了才退出循环
                    isNormal = true;
                } catch (Exception e) {
                    retryCount++;
                    System.out.println("第 " + retryCount + " 次创建失败，重试中...");
                    if (retryCount >= MAX_RETRY) {
                        System.out.println("创建失败次数超过 " + MAX_RETRY + " 次，终止任务。");
                        throw new RuntimeException("创建网络负载平衡器重试失败次数超过限制", e);
                    }
                    Thread.sleep(30000);
                }
            }

            while (!networkLoadBalancerClient.getNetworkLoadBalancer(GetNetworkLoadBalancerRequest.builder()
                    .networkLoadBalancerId(networkLoadBalancer.getId())
                    .build()).getNetworkLoadBalancer().getLifecycleState().getValue().equals(LifecycleState.Active.getValue())) {
                Thread.sleep(1000);
            }

            System.out.println("网络负载平衡器创建成功");
            networkLoadBalancerClient.getNetworkLoadBalancer(GetNetworkLoadBalancerRequest.builder()
                    .networkLoadBalancerId(networkLoadBalancer.getId())
                    .build()).getNetworkLoadBalancer().getIpAddresses().forEach(x -> {
                if (!CommonUtils.isPrivateIp(x.getIpAddress())) {
                    System.out.println("公网IP：" + x.getIpAddress());
                }
            });

            // NAT网关
            NatGateway natGateway;
            List<NatGateway> natGatewayList = virtualNetworkClient.listNatGateways(ListNatGatewaysRequest.builder()
                    .compartmentId(compartmentId)
                    .lifecycleState(NatGateway.LifecycleState.Available)
                    .vcnId(vcn.getId())
                    .build()).getItems();
            if (CollectionUtil.isNotEmpty(natGatewayList)) {
                natGateway = natGatewayList.getFirst();
                System.out.println("获取到已存在的NAT网关：" + natGateway.getDisplayName());
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
                System.out.println("NAT网关创建成功：" + natGateway.getDisplayName());
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
                virtualNetworkClient.updateRouteTable(UpdateRouteTableRequest.builder()
                        .rtId(routeTable.getId())
                        .updateRouteTableDetails(UpdateRouteTableDetails.builder()
                                .routeRules(Collections.singletonList(RouteRule.builder()
                                        .cidrBlock("0.0.0.0/0") // 所有流量
                                        .networkEntityId(natGateway.getId())
                                        .destinationType(RouteRule.DestinationType.CidrBlock)
                                        .build()))
                                .build())
                        .build());
                System.out.println("获取到已存在的NAT路由表：" + routeTable.getDisplayName());
            } else {
                routeTable = virtualNetworkClient.createRouteTable(CreateRouteTableRequest.builder()
                        .createRouteTableDetails(CreateRouteTableDetails.builder()
                                .compartmentId(compartmentId)
                                .vcnId(vcn.getId())
                                .displayName("nat-route")
                                .routeRules(Collections.singletonList(RouteRule.builder()
                                        .cidrBlock("0.0.0.0/0") // 所有流量
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

                System.out.println("NAT路由表创建成功：" + routeTable.getDisplayName());
            }


            // 实例vnic绑定路由表，跳过源/目的地检查
            virtualNetworkClient.updateVnic(UpdateVnicRequest.builder()
                    .vnicId(instanceVnicId)
                    .updateVnicDetails(UpdateVnicDetails.builder()
                            .skipSourceDestCheck(true)
                            .routeTableId(routeTable.getId())
                            .build())
                    .build());

            System.out.println("实例vnic绑定路由表成功");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    void test2() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 1000; i++) {
                executor.submit(() -> {
                    Thread.sleep(Duration.ofSeconds(1));
                    System.out.println("任务完成");
                    return "结果";
                });
            }
        }

        System.out.println(CommonUtils.getLatestVersion());
    }

    @Test
    void test3() throws InterruptedException {
        // 添加键值对，分别设置不同的过期时间
        customCache.put("key1", "value1", 2000); // 2秒
        customCache.put("key2", "value2", 5000); // 5秒

        // 获取值
        System.out.println("Key1: " + customCache.get("key1")); // 立即获取
        Thread.sleep(3000); // 等待3秒
        System.out.println("Key1: " + customCache.get("key1")); // 过期，返回null
        System.out.println("Key2: " + customCache.get("key2")); // 未过期，返回value2
    }

}
