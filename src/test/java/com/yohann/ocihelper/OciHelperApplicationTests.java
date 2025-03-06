package com.yohann.ocihelper;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.oracle.bmc.core.model.SecurityList;
import com.oracle.bmc.monitoring.MonitoringClient;
import com.oracle.bmc.monitoring.model.*;
import com.oracle.bmc.monitoring.requests.ListMetricsRequest;
import com.oracle.bmc.monitoring.requests.PostMetricDataRequest;
import com.oracle.bmc.monitoring.requests.SummarizeMetricsDataRequest;
import com.oracle.bmc.monitoring.responses.SummarizeMetricsDataResponse;
import com.oracle.bmc.usage.UsagelimitsClient;
import com.oracle.bmc.usage.model.UsageLimitSummary;
import com.oracle.bmc.usage.requests.ListUsageLimitsRequest;
import com.oracle.bmc.usage.responses.ListUsageLimitsResponse;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.bean.response.oci.securityrule.SecurityRuleListRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.service.IInstanceService;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;


@SpringBootTest
class OciHelperApplicationTests {

    @Resource
    private IInstanceService instanceService;
    @Resource
    private CustomExpiryGuavaCache<String, Object> customCache;

    @Test
    void contextLoads() throws IOException {
        String s = FileUtil.readString("C:\\Users\\Yohann\\Desktop\\test.txt", Charset.defaultCharset());
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
                        .privateKeyPath("C:\\Users\\Yohann\\Desktop\\" + ociUser.getOciKeyPath())
                        .build())
                .username(ociUser.getUsername())
                .build();

        System.out.println(sysUserDTO);

        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO);) {
            SummarizeMetricsDataResponse summarizeMetricsDataResponse = fetcher.getMonitoringClient()
                    .summarizeMetricsData(SummarizeMetricsDataRequest.builder()
                            .compartmentId(fetcher.getCompartmentId())
                            .summarizeMetricsDataDetails(SummarizeMetricsDataDetails.builder()
                                    .namespace("oci_vcn")
                                    .query("VnicFromNetworkBytes[1440m]{resourceId = \"ocid1.vnic.oc1.sa-saopaulo-1.abtxeljracruzyrxlywlznnz6d7frxfgok3pnrliawamiz65yzgr3gapvqda\"}.sum()")
                                    .startTime(CommonUtils.localDateTime2Date(LocalDateTime.of(2025, 3, 1, 0, 0, 0)))
                                    .endTime(CommonUtils.localDateTime2Date(LocalDateTime.of(2025, 3, 7, 0, 0, 0)))
                                    .build())
                            .build());
            List<MetricData> items = summarizeMetricsDataResponse.getItems();
            System.out.println(JSONUtil.toJsonStr(items));
            for (MetricData item : items) {
                System.out.println("------------------------------------");
                item.getAggregatedDatapoints().forEach(x -> {
                    System.out.println(CommonUtils.dateFmt2String(x.getTimestamp()) + " = " + x.getValue().longValue() + " B");
                });
                System.out.println("------------------------------------");
            }

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
