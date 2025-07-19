package com.yohann.ocihelper;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.model.*;
import com.oracle.bmc.core.requests.*;
import com.oracle.bmc.core.responses.*;
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
            String instanceId = "ocid1.instance.oc1.sa-saopaulo-1.antxeljrnc5vuiqcoj22nt52eyehxnhe4dowp543bggqteaeqoklv27jqxua";
            ComputeClient computeClient = fetcher.getComputeClient();
            BlockstorageClient blockstorageClient = fetcher.getBlockstorageClient();

            BootVolume bootVolumeByInstanceId = fetcher.getBootVolumeByInstanceId(instanceId);

            // 先关机
            System.out.println("=====================（1/9）⌛ 正在关机=====================");
            computeClient.instanceAction(InstanceActionRequest.builder()
                    .instanceId(instanceId)
                    .action(InstanceActionEnum.ACTION_STOP.getAction())
                    .build());
            System.out.println("=====================（1/9）⌛ 关机成功=====================");

            while (!fetcher.getInstanceById(instanceId).getLifecycleState().getValue().equals(Instance.LifecycleState.Stopped.getValue())) {
                Thread.sleep(1000);
            }

            // 备份原引导卷
            System.out.println("=====================（2/9）⌛ 正在备份原引导卷=====================");
            CreateBootVolumeBackupResponse bootVolumeBackup = blockstorageClient.createBootVolumeBackup(CreateBootVolumeBackupRequest.builder()
                    .createBootVolumeBackupDetails(CreateBootVolumeBackupDetails.builder()
                            .type(CreateBootVolumeBackupDetails.Type.Full)
                            .bootVolumeId(bootVolumeByInstanceId.getId())
                            .displayName("Old-BootVolume-Backup")
                            .build())
                    .build());
            BootVolumeBackup oldBootVolumeBackup = bootVolumeBackup.getBootVolumeBackup();
            System.out.println("=====================（2/9）⌛ 备份原引导卷成功=====================");

            Thread.sleep(3000);

            // 分离原引导卷
            System.out.println("=====================（3/9）⌛ 正在分离原引导卷=====================");
            computeClient.detachBootVolume(DetachBootVolumeRequest.builder()
                    .bootVolumeAttachmentId(instanceId)
                    .build());
            System.out.println("=====================（3/9）⌛ 分离原引导卷成功=====================");

            while (!blockstorageClient.getBootVolumeBackup(GetBootVolumeBackupRequest.builder()
                            .bootVolumeBackupId(oldBootVolumeBackup.getId())
                            .build()).getBootVolumeBackup().getLifecycleState().getValue()
                    .equals(BootVolumeBackup.LifecycleState.Available.getValue())) {
                Thread.sleep(1000);
            }

            // 删除原引导卷
            System.out.println("=====================（4/9）⌛ 正在删除原引导卷=====================");
            blockstorageClient.deleteBootVolume(DeleteBootVolumeRequest.builder()
                    .bootVolumeId(bootVolumeByInstanceId.getId())
                    .build());
            System.out.println("=====================（4/9）⌛ 删除原引导卷成功=====================");

            while (!blockstorageClient.getBootVolume(GetBootVolumeRequest.builder()
                    .bootVolumeId(bootVolumeByInstanceId.getId())
                    .build()).getBootVolume().getLifecycleState().getValue().equals(BootVolume.LifecycleState.Terminated.getValue())) {
                Thread.sleep(1000);
            }

            // 创建50GB的AMD机器
            System.out.println("=====================（5/9）⌛ 正在创建AMD机器=====================");
            SysUserDTO newAmd = SysUserDTO.builder()
                    .ociCfg(SysUserDTO.OciCfg.builder()
                            .userId(ociUser.getOciUserId())
                            .tenantId(ociUser.getOciTenantId())
                            .region(ociUser.getOciRegion())
                            .fingerprint(ociUser.getOciFingerprint())
                            .privateKeyPath(ociUser.getOciKeyPath())
                            .build())
                    .username(ociUser.getUsername())
                    .ocpus(1.0F)
                    .memory(1.0F)
                    .architecture("AMD")
                    .createNumbers(1)
                    .operationSystem("Ubuntu")
                    .rootPassword("ocihelper2024")
                    .build();
            fetcher.setUser(newAmd);
            InstanceDetailDTO instanceData = fetcher.createInstanceData();
            if (instanceData.isNoShape()) {
                throw new OciException(-1, "当前区域无法创建AMD实例");
            }
            Instance newAmdInstance = instanceData.getInstance();
            System.out.println("=====================（5/9）⌛ AMD机器创建成功=====================");

            // 克隆新建实例引导卷
            System.out.println("=====================（6/9）⌛ 正在克隆新建实例引导卷=====================");
            BootVolume newAmdInstanceBootVolume = fetcher.getBootVolumeByInstanceId(newAmdInstance.getId());
            CreateBootVolumeResponse cloneBootVolume = blockstorageClient.createBootVolume(CreateBootVolumeRequest.builder()
                    .createBootVolumeDetails(CreateBootVolumeDetails.builder()
                            .compartmentId(fetcher.getCompartmentId())
                            .availabilityDomain(bootVolumeByInstanceId.getAvailabilityDomain())
                            .sourceDetails(BootVolumeSourceFromBootVolumeDetails.builder()
                                    .id(newAmdInstanceBootVolume.getId())
                                    .build())
                            .displayName("Cloned-Boot-Volume")
                            .build())
                    .build());
            BootVolume newAmdInstanceCloneBootVolume = cloneBootVolume.getBootVolume();
            System.out.println("=====================（6/9）⌛ 新建实例引导卷克隆成功=====================");

            while (!blockstorageClient.getBootVolume(GetBootVolumeRequest.builder()
                            .bootVolumeId(newAmdInstanceCloneBootVolume.getId())
                            .build()).getBootVolume().getLifecycleState().getValue()
                    .equals(BootVolume.LifecycleState.Available.getValue())) {
                Thread.sleep(1000);
            }

            // 将新建实例的克隆引导卷附加到需要救砖的实例
            System.out.println("=====================（7/9）⌛ 正在将新建实例的克隆引导卷附加到需要救砖的实例=====================");
            AttachBootVolumeResponse attachedBootVolume = computeClient.attachBootVolume(AttachBootVolumeRequest.builder()
                    .attachBootVolumeDetails(AttachBootVolumeDetails.builder()
                            .displayName("New-Boot-Volume")
                            .bootVolumeId(newAmdInstanceCloneBootVolume.getId())
                            .instanceId(instanceId)
                            .build())
                    .build());
            System.out.println("=====================（7/9）⌛ 新建实例的克隆引导卷附加到需要救砖的实例成功=====================");
            System.out.println(JSONUtil.toJsonStr(attachedBootVolume.getBootVolumeAttachment()));

            while (!fetcher.getBootVolumeById(attachedBootVolume.getBootVolumeAttachment().getBootVolumeId())
                    .getLifecycleState().getValue()
                    .equals(BootVolume.LifecycleState.Available.getValue())) {
                Thread.sleep(1000);
            }

            System.out.println("=====================（8/9）⌛ 正在删除新建的实例、引导卷、备份卷=====================");
            fetcher.terminateInstance(newAmdInstance.getId(), false, false);
            blockstorageClient.deleteBootVolumeBackup(DeleteBootVolumeBackupRequest.builder()
                    .bootVolumeBackupId(oldBootVolumeBackup.getId())
                    .build());
            System.out.println("=====================（8/9）⌛ 删除新建的实例、引导卷、备份卷成功=====================");

            Thread.sleep(3000);

            System.out.println("=====================（9/9）⌛ 实例救援成功，正在启动实例...=====================");
            while (!fetcher.getInstanceById(instanceId).getLifecycleState().getValue().equals(Instance.LifecycleState.Running.getValue())) {
                try {
                    computeClient.instanceAction(InstanceActionRequest.builder()
                            .instanceId(instanceId)
                            .action(InstanceActionEnum.ACTION_START.getAction())
                            .buildWithoutInvocationCallback());
                } catch (Exception e) {

                }
                Thread.sleep(1000);
            }
            System.out.println("=====================（9/9）🎉 实例启动成功 🎉=====================");
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
