package com.yohann.ocihelper;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.VirtualNetworkClient;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.model.*;
import com.oracle.bmc.identity.requests.*;
import com.oracle.bmc.identity.responses.ListDomainsResponse;
import com.oracle.bmc.identitydomains.IdentityDomainsClient;
import com.oracle.bmc.identitydomains.model.PasswordPolicies;
import com.oracle.bmc.identitydomains.model.PasswordPolicy;
import com.oracle.bmc.identitydomains.requests.ListPasswordPoliciesRequest;
import com.oracle.bmc.identitydomains.requests.PutPasswordPolicyRequest;
import com.oracle.bmc.identitydomains.responses.ListPasswordPoliciesResponse;
import com.oracle.bmc.identitydomains.responses.PutPasswordPolicyResponse;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.bean.response.oci.tenant.TenantInfoRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.IInstanceService;
import com.yohann.ocihelper.service.ITenantService;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import com.yohann.ocihelper.utils.OciUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import jakarta.annotation.Resource;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class OciHelperApplicationTests {

    private static final Logger log = LoggerFactory.getLogger(OciHelperApplicationTests.class);


    @MockBean // Mock 掉，不让它真正注册
    private ServerEndpointExporter serverEndpointExporter;

    @Resource
    private IInstanceService instanceService;
    @Resource
    private ITenantService tenantService;
    @Resource
    private ExecutorService virtualExecutor;
    @Resource
    private CustomExpiryGuavaCache<String, Object> customCache;

    @Test
    void contextLoads() throws IOException {
        String baseDir = "C:\\Users\\yohann_fan\\Desktop\\test\\oci-helper\\";
//        String baseDir = "C:\\Users\\Yohann\\Desktop\\";
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

//        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO);) {
//            IdentityClient identityClient = fetcher.getIdentityClient();
//            String compartmentId = fetcher.getCompartmentId();
//            ComputeClient computeClient = fetcher.getComputeClient();
//            VirtualNetworkClient virtualNetworkClient = fetcher.getVirtualNetworkClient();
//            IdentityDomainsClient identityDomainsClient = fetcher.getIdentityDomainsClient();
//
//            OciUtils.Result currentRecipients = OciUtils.getCurrentRecipients(fetcher);
//            System.out.println(currentRecipients);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        TenantInfoRsp rsp = new TenantInfoRsp();
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            IdentityClient identityClient = fetcher.getIdentityClient();
            Tenancy tenancy = identityClient.getTenancy(GetTenancyRequest.builder()
                    .tenancyId(sysUserDTO.getOciCfg().getTenantId())
                    .build()).getTenancy();
            BeanUtils.copyProperties(tenancy, rsp);

            CompletableFuture<List<TenantInfoRsp.TenantUserInfo>> userListTask = CompletableFuture.supplyAsync(() ->
                            Optional.ofNullable(identityClient.listUsers(ListUsersRequest.builder()
                                            .compartmentId(fetcher.getCompartmentId())
                                            .build()).getItems())
                                    .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).stream()
                                    .map(x -> {
                                        TenantInfoRsp.TenantUserInfo info = new TenantInfoRsp.TenantUserInfo();
                                        info.setId(x.getId());
                                        info.setName(x.getName());
                                        info.setEmail(x.getEmail());
                                        info.setLifecycleState(x.getLifecycleState().getValue());
                                        info.setEmailVerified(x.getEmailVerified());
                                        info.setIsMfaActivated(x.getIsMfaActivated());
                                        info.setTimeCreated(CommonUtils.dateFmt2String(x.getTimeCreated()));
                                        info.setLastSuccessfulLoginTime(x.getLastSuccessfulLoginTime() == null ? null : CommonUtils.dateFmt2String(x.getLastSuccessfulLoginTime()));
                                        info.setJsonStr(JSONUtil.toJsonStr(x));
                                        return info;
                                    }).collect(Collectors.toList()), virtualExecutor)
                    .exceptionally(e -> {
                        log.error("get user list error", e);
                        return Collections.emptyList();
                    });

            CompletableFuture<List<String>> regionsTask = CompletableFuture.supplyAsync(() ->
                            identityClient.listRegionSubscriptions(ListRegionSubscriptionsRequest.builder()
                                            .tenancyId(sysUserDTO.getOciCfg().getTenantId())
                                            .build()).getItems().stream()
                                    .map(RegionSubscription::getRegionName)
                                    .collect(Collectors.toList()), virtualExecutor)
                    .exceptionally(e -> {
                        log.error("get region list error", e);
                        return Collections.emptyList();
                    });

            CompletableFuture<PasswordPolicy> pwdExpTask = CompletableFuture.supplyAsync(() -> {
                        List<PasswordPolicy> passwordPolicyList = OciUtils.getCurrentPasswordPolicy(fetcher);
                        return passwordPolicyList.parallelStream()
                                .filter(x -> x.getPasswordExpiresAfter() != null && x.getPasswordExpiresAfter() >= 0)
                                .findAny()
                                .orElse(PasswordPolicy.builder().build());
                    }, virtualExecutor)
                    .exceptionally(e -> {
                        log.error("get pwd expires after error", e);
                        return PasswordPolicy.builder().build();
                    });

            CompletableFuture<String> createTimeTask = CompletableFuture.supplyAsync(() -> {
                        String registeredTime = fetcher.getRegisteredTime();
                        String timeDifference = CommonUtils.getTimeDifference(LocalDateTime.parse(registeredTime, CommonUtils.DATETIME_FMT_NORM));
                        return registeredTime + "（" + timeDifference + "）";
                    }, virtualExecutor)
                    .exceptionally(e -> {
                        log.error("get account create time error", e);
                        return null;
                    });

            CompletableFuture.allOf(userListTask, regionsTask, pwdExpTask, createTimeTask).join();

            rsp.setUserList(CommonUtils.safeJoin(userListTask, Collections.emptyList()));
            rsp.setRegions(CommonUtils.safeJoin(regionsTask, Collections.emptyList()));
            rsp.setPasswordExpiresAfter(CommonUtils.safeJoin(pwdExpTask, PasswordPolicy.builder().build()).getPasswordExpiresAfter());
            rsp.setCreatTime(CommonUtils.safeJoin(createTimeTask, null));
        } catch (Exception e) {
            log.error("获取租户信息失败", e);
            throw new OciException(-1, "获取租户信息失败", e);
        }

        System.out.println(JSONUtil.toJsonStr(rsp));
    }

    @Test
    void test2() {
//        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
//            for (int i = 0; i < 1000; i++) {
//                executor.submit(() -> {
//                    Thread.sleep(Duration.ofSeconds(1));
//                    System.out.println("任务完成");
//                    return "结果";
//                });
//            }
//        }
//
//        System.out.println(CommonUtils.getLatestVersion());
    }

    @Test
    void test3() throws InterruptedException {
//        // 添加键值对，分别设置不同的过期时间
//        customCache.put("key1", "value1", 2000); // 2秒
//        customCache.put("key2", "value2", 5000); // 5秒
//
//        // 获取值
//        System.out.println("Key1: " + customCache.get("key1")); // 立即获取
//        Thread.sleep(3000); // 等待3秒
//        System.out.println("Key1: " + customCache.get("key1")); // 过期，返回null
//        System.out.println("Key2: " + customCache.get("key2")); // 未过期，返回value2
    }

}
