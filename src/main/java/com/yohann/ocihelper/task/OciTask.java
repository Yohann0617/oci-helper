package com.yohann.ocihelper.task;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciCreateTask;
import com.yohann.ocihelper.bean.entity.OciKv;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.enums.SysCfgEnum;
import com.yohann.ocihelper.service.*;
import com.yohann.ocihelper.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private IOciKvService kvService;
    @Resource
    private ISysService sysService;
    @Resource
    private IOciCreateTaskService createTaskService;

    @Value("${web.account}")
    private String account;
    @Value("${web.password}")
    private String password;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        TEMP_MAP.put("password", password);
        cleanLogTask();
        cleanAndRestartTask();
        initGenMfaPng();
    }

    private void cleanLogTask() {
        addAtFixedRateTask(account, () -> {
            try (FileWriter fw = new FileWriter(CommonUtils.LOG_FILE_PATH, false)) {
                fw.write("");
                log.info("【日志清理任务】日志文件：{} 已清空", CommonUtils.LOG_FILE_PATH);
            } catch (IOException e) {
                log.error("【日志清理任务】清理日志文件时出错：{}", e.getMessage());
            }
        }, 4, 4, TimeUnit.HOURS);
    }

    private void cleanAndRestartTask() {
        Optional.ofNullable(createTaskService.list())
                .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).stream()
                .forEach(task -> {
                    if (task.getCreateNumbers() <= 0) {
                        createTaskService.removeById(task.getId());
                    } else {
                        OciUser ociUser = userService.getById(task.getUserId());
                        SysUserDTO sysUserDTO = SysUserDTO.builder()
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
                                .build();
                        addTask(CommonUtils.CREATE_TASK_PREFIX + task.getId(), () ->
                                        execCreate(sysUserDTO, sysService, instanceService, createTaskService),
                                0, task.getInterval(), TimeUnit.SECONDS);
                    }
                });
    }

    private void initGenMfaPng() {
        Optional.ofNullable(kvService.getOne(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode()))).ifPresent(mfa -> {
            String qrCodeURL = CommonUtils.generateQRCodeURL(mfa.getValue(), account, "oci-helper");
            CommonUtils.genQRPic(CommonUtils.MFA_QR_PNG_PATH, qrCodeURL);
        });
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void dailyBroadcastTask() {
        String message = "【每日播报】\n" +
                "------------------------------------\n" +
                "时间：\t%s\n" +
                "总API配置数：\t%s\n" +
                "失效API配置数：\t%s\n" +
                "失效的API配置：\t%s\n" +
                "正在执行的开机任务：\n" +
                "%s\n" +
                "------------------------------------";
        List<String> ids = userService.listObjs(new LambdaQueryWrapper<OciUser>()
                .isNotNull(OciUser::getId)
                .select(OciUser::getId), String::valueOf);

        CompletableFuture<List<?>> fails = CompletableFuture.supplyAsync(() -> {
            if (ids.isEmpty()) {
                return Collections.emptyList();
            }
            return ids.parallelStream().filter(id -> {
                SysUserDTO ociUser = sysService.getOciUser(id);
                try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(ociUser)) {
                    fetcher.getAvailabilityDomains();
                } catch (Exception e) {
                    return true;
                }
                return false;
            }).map(id -> sysService.getOciUser(id).getUsername()).collect(Collectors.toList());
        });

        CompletableFuture<String> task = CompletableFuture.supplyAsync(() -> {
            List<OciCreateTask> ociCreateTaskList = createTaskService.list();
            if (ociCreateTaskList.isEmpty()) {
                return "无";
            }
            String template = "[%s] [%s] [%s] [%s核/%sGB/%sGB] [%s台] [%s次]";
            return ociCreateTaskList.parallelStream().map(x -> {
                OciUser ociUser = userService.getById(x.getUserId());
                Long counts = (Long) TEMP_MAP.get(CommonUtils.CREATE_COUNTS_PREFIX + x.getId());
                return String.format(template, ociUser.getUsername(), ociUser.getOciRegion(), x.getArchitecture(),
                        x.getOcpus(), x.getMemory(), x.getDisk(),
                        x.getCreateNumbers(), counts == null ? "0" : counts);
            }).collect(Collectors.joining("\n"));
        });

        CompletableFuture.allOf(fails, task).join();

        sysService.sendMessage(String.format(message,
                LocalDateTime.now().format(CommonUtils.DATETIME_FMT_NORM),
                CollectionUtil.isEmpty(ids) ? 0 : ids.size(),
                fails.join().size(),
                fails.join(),
                task.join()
        ));
    }
}
