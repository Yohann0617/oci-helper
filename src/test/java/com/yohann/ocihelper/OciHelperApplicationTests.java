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
import com.oracle.bmc.networkloadbalancer.requests.CreateNetworkLoadBalancerRequest;
import com.oracle.bmc.networkloadbalancer.requests.UpdateNetworkLoadBalancerRequest;
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
//        String baseDir = "C:\\Users\\yohann_fan\\Desktop\\test\\oci-helper\\";
        String baseDir = "C:\\Users\\Yohann\\Desktop\\";
        String s = FileUtil.readString(baseDir + "test.txt", Charset.defaultCharset());
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

            // 选择一个vcn
            List<Vcn> vcns = fetcher.listVcn();
            Vcn vcn = vcns.getFirst();
            // 选择一个子网
            Subnet subnet = virtualNetworkClient.listSubnets(ListSubnetsRequest.builder()
                    .compartmentId(compartmentId)
                    .vcnId(vcn.getId())
                    .build()).getItems().getFirst();
            // 网络安全组（不一定用得上）
            List<NetworkSecurityGroup> networkSecurityGroups = fetcher.listNetworkSecurityGroups();
            String nsgID = null;
            if (CollectionUtil.isEmpty(networkSecurityGroups)) {
                NetworkSecurityGroup networkSecurityGroup = fetcher.createNetworkSecurityGroup(virtualNetworkClient, compartmentId, vcn);
                nsgID = networkSecurityGroup.getId();
                fetcher.addNetworkSecurityGroupSecurityRules(virtualNetworkClient, networkSecurityGroup, "0.0.0.0/0");
                fetcher.addNetworkSecurityGroupSecurityRules(virtualNetworkClient, networkSecurityGroup, "::/0");
            }

            // 网络负载平衡器
            NetworkLoadBalancerClient networkLoadBalancerClient = fetcher.getNetworkLoadBalancerClient();
            networkLoadBalancerClient.createNetworkLoadBalancer(CreateNetworkLoadBalancerRequest.builder()
                    .createNetworkLoadBalancerDetails(CreateNetworkLoadBalancerDetails.builder()
                            .compartmentId(compartmentId)
                            .isPrivate(false)
                            .subnetId(subnet.getId())
                            .networkSecurityGroupIds(Collections.singletonList(nsgID))
                            .nlbIpVersion(NlbIpVersion.Ipv4AndIpv6)
                            .listeners(Map.of(
                                    "listener1", ListenerDetails.builder()
                                            .protocol(ListenerProtocols.TcpAndUdp)
                                            .build()
                            ))
                            .backendSets(Map.of(
                                    "backend1", BackendSetDetails.builder()
                                            .isPreserveSource(true)
                                            .isFailOpen(true)
                                            .policy(NetworkLoadBalancingPolicy.TwoTuple)
                                            .healthChecker(HealthChecker.builder()
                                                    .protocol(HealthCheckProtocols.Tcp)
                                                    .port(22)
                                                    .build())
                                            .backends(Collections.singletonList(Backend.builder()
                                                    .targetId("") // TODO: 2025/8/12
                                                    .ipAddress("") // TODO: 2025/8/12
                                                    .weight(1)
                                                    .build()))
                                            .build()
                            ))
                            .build())
                    .build());

            // NAT网关
            NatGateway natGateway = virtualNetworkClient.createNatGateway(CreateNatGatewayRequest.builder()
                    .createNatGatewayDetails(CreateNatGatewayDetails.builder()
                            .vcnId(vcn.getId())
                            .compartmentId(compartmentId)
                            .displayName("nat-gateway")
                            .blockTraffic(true)
                            .build())
                    .build()).getNatGateway();

            // 路由表
            RouteTable routeTable = virtualNetworkClient.createRouteTable(CreateRouteTableRequest.builder()
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

            // 实例vnic绑定路由表，跳过源/目的地检查

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
