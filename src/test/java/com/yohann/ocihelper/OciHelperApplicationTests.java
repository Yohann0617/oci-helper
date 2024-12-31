package com.yohann.ocihelper;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.PageUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.oracle.bmc.core.model.BootVolume;
import com.oracle.bmc.identity.model.AvailabilityDomain;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.bean.response.oci.BootVolumeListPage;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.service.IInstanceService;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;


@SpringBootTest
class OciHelperApplicationTests {

    @Resource
    private IInstanceService instanceService;
    @Resource
    private CustomExpiryGuavaCache<String, Object> customCache;

    @Test
    void contextLoads() throws IOException {
        String s = FileUtil.readString("C:\\Users\\Administrator\\Desktop\\test.txt", Charset.defaultCharset());
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
                        .privateKeyPath(ociUser.getOciKeyPath())
                        .build())
                .username(ociUser.getUsername())
                .build();

        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            List<AvailabilityDomain> availabilityDomains = fetcher.getAvailabilityDomains();
            System.out.println("-----------------------------------");
            availabilityDomains.forEach(System.out::println);
            System.out.println("-----------------------------------");
            List<BootVolume> bootVolumes = fetcher.listBootVolume();
            bootVolumes.forEach(System.out::println);
            System.out.println("-----------------------------------");
            List<BootVolumeListPage.BootVolumeInfo> collect = bootVolumes.parallelStream().map(x -> {
                BootVolumeListPage.BootVolumeInfo bootVolumeInfo = new BootVolumeListPage.BootVolumeInfo();
                bootVolumeInfo.setId(x.getId());
                bootVolumeInfo.setAvailabilityDomain(x.getAvailabilityDomain());
                bootVolumeInfo.setDisplayName(x.getDisplayName());
                bootVolumeInfo.setVpusPerGB(x.getVpusPerGB() + "");
                bootVolumeInfo.setSizeInGBs(x.getSizeInGBs() + "");
                bootVolumeInfo.setLifecycleState(x.getLifecycleState().getValue());
                bootVolumeInfo.setTimeCreated(DateUtil.format(x.getTimeCreated(), CommonUtils.DATETIME_FMT_NORM));
                bootVolumeInfo.setJsonStr(JSONUtil.toJsonStr(x));
                return bootVolumeInfo;
            }).collect(Collectors.toList());
            List<BootVolumeListPage.BootVolumeInfo> page = CommonUtils.getPage(collect, 1, 5);
            page.forEach(System.out::println);
            System.out.println("-----------------------------------");
            System.out.println("total:" + page.size());
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
