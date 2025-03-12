package com.yohann.ocihelper;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.model.Shape;
import com.oracle.bmc.core.requests.ListShapesRequest;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.model.AvailabilityDomain;
import com.oracle.bmc.identity.model.CreateApiKeyDetails;
import com.oracle.bmc.identity.model.CreateRegionSubscriptionDetails;
import com.oracle.bmc.identity.model.User;
import com.oracle.bmc.identity.requests.*;
import com.oracle.bmc.identity.responses.GetTenancyResponse;
import com.oracle.bmc.identity.responses.ListRegionsResponse;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.service.IInstanceService;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
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
        String baseDir = "C:\\Users\\yohann_fan\\Desktop\\";
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
            ComputeClient computeClient = fetcher.getComputeClient();

//            identityClient.listUsers(ListUsersRequest.builder()
//                    .compartmentId(fetcher.getCompartmentId())
//                    .build()).getItems().forEach(x->{
//                System.out.println(JSONUtil.toJsonStr(x));
//            });

//            System.out.println(JSONUtil.toJsonStr(fetcher.getUserInfo()));
//
            GetTenancyResponse tenancy = identityClient.getTenancy(GetTenancyRequest.builder()
                    .tenancyId(sysUserDTO.getOciCfg().getTenantId())
                    .build());
            System.out.println(JSONUtil.toJsonStr(tenancy.getTenancy()));

//            List<AvailabilityDomain> availabilityDomains = fetcher.getAvailabilityDomains();
//            availabilityDomains.parallelStream().map(availabilityDomain ->
//                            computeClient.listShapes(ListShapesRequest.builder()
//                                    .availabilityDomain(availabilityDomain.getName())
//                                    .compartmentId(fetcher.getCompartmentId())
//                                    .build()).getItems())
//                    .flatMap(Collection::stream)
//                    .map(Shape::getShape)
//                    .collect(Collectors.toList()).forEach(System.out::println);


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    void test2() {
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
