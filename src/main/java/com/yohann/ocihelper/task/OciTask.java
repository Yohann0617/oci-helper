package com.yohann.ocihelper.task;

import cn.hutool.core.collection.CollectionUtil;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.service.IInstanceService;
import com.yohann.ocihelper.service.IOciCreateTaskService;
import com.yohann.ocihelper.service.IOciUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.yohann.ocihelper.service.impl.OciServiceImpl.*;

/**
 * <p>
 * OciTask
 * </p >
 *
 * @author yohann
 * @since 2024/11/1 19:21
 */
@Slf4j
@Component
public class OciTask implements ApplicationRunner {

    @Resource
    private IInstanceService instanceService;
    @Resource
    private IOciUserService userService;
    @Resource
    private IOciCreateTaskService createTaskService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 开机任务重启，清理已完成的开机任务
        Optional.ofNullable(createTaskService.list())
                .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
                .forEach(task -> {
                    if (task.getCreateNumbers() <= 0) {
                        createTaskService.removeById(task.getId());
                    } else {
                        OciUser ociUser = userService.getById(task.getUserId());
                        OracleInstanceFetcher fetcher = new OracleInstanceFetcher(SysUserDTO.builder()
                                .ociCfg(SysUserDTO.OciCfg.builder()
                                        .userId(ociUser.getOciUserId())
                                        .tenantId(ociUser.getOciTenantId())
                                        .region(ociUser.getOciRegion())
                                        .fingerprint(ociUser.getOciFingerprint())
                                        .privateKeyPath(ociUser.getOciKeyPath())
                                        .build())
                                .taskId(task.getId())
                                .username(ociUser.getUsername())
                                .ocpus(task.getOcpus())
                                .memory(task.getMemory())
                                .disk(Long.valueOf(task.getDisk()))
                                .architecture(task.getArchitecture())
                                .interval(Long.valueOf(task.getInterval()))
                                .createNumbers(task.getCreateNumbers())
                                .operationSystem(task.getOperationSystem())
                                .rootPassword(task.getRootPassword())
                                .build());
                        CREATE_INSTANCE_POOL.scheduleWithFixedDelay(() -> execCreate(fetcher, instanceService),
                                0, task.getInterval(), TimeUnit.SECONDS);
                    }
                });
    }

}
