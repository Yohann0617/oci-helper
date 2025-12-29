package com.yohann.ocihelper.task;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oracle.bmc.Region;
import com.oracle.bmc.identity.model.Tenancy;
import com.oracle.bmc.identity.requests.GetTenancyRequest;
import com.yohann.ocihelper.bean.constant.CacheConstant;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.IpData;
import com.yohann.ocihelper.bean.entity.OciCreateTask;
import com.yohann.ocihelper.bean.entity.OciKv;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.enums.EnableEnum;
import com.yohann.ocihelper.enums.OciUnSupportRegionEnum;
import com.yohann.ocihelper.enums.SysCfgEnum;
import com.yohann.ocihelper.enums.SysCfgTypeEnum;
import com.yohann.ocihelper.service.*;
import com.yohann.ocihelper.telegram.TgBot;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.SQLiteHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
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
    private IIpDataService ipDataService;
    @Resource
    private IOciCreateTaskService createTaskService;
    @Resource
    private TaskScheduler taskScheduler;
    @Resource
    private SQLiteHelper sqLiteHelper;
    @Resource
    private ExecutorService virtualExecutor;

    private static volatile boolean isPushedLatestVersion = false;
    public static volatile TelegramBotsLongPollingApplication botsApplication;

    @Value("${web.account}")
    private String account;
    @Value("${web.password}")
    private String password;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        TEMP_MAP.put("password", password);
        startTgBog();
        updateUserInDb();
        cleanLogTask();
        cleanAndRestartTask();
        initGenMfaPng();
        saveVersion();
        startInform();
        pushVersionUpdateMsg(kvService, sysService);
        dailyBroadcastTask();
        supportOciUnknownRegionTask();
        initMapData();
    }

    private void startTgBog() {
        virtualExecutor.execute(() -> {
            OciKv tgToken = kvService.getOne(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, SysCfgEnum.SYS_TG_BOT_TOKEN.getCode()));
            OciKv tgChatId = kvService.getOne(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, SysCfgEnum.SYS_TG_CHAT_ID.getCode()));
            if (null == tgToken || null == tgChatId) {
                return;
            }
            if (StrUtil.isNotBlank(tgToken.getValue()) && StrUtil.isNotBlank(tgChatId.getValue())) {
                botsApplication = new TelegramBotsLongPollingApplication();
                try {
                    botsApplication.registerBot(tgToken.getValue(), new TgBot(tgToken.getValue(), tgChatId.getValue()));
                    Thread.currentThread().join();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                log.info("TG Bot successfully started");
            }
        });
    }

    private void cleanLogTask() {
        addAtFixedRateTask(account, () -> {
            FileUtil.writeUtf8String("", CommonUtils.LOG_FILE_PATH);
            log.info("【日志清理任务】日志文件：{} 已清空", CommonUtils.LOG_FILE_PATH);
        }, 8, 8, TimeUnit.HOURS);
    }

    private void updateUserInDb() {
        sqLiteHelper.addColumnIfNotExists("oci_user", "tenant_name", "VARCHAR(64) NULL");
        sqLiteHelper.addColumnIfNotExists("oci_create_task", "oci_region", "VARCHAR(64) NULL");
        sqLiteHelper.addColumnIfNotExists("oci_user", "tenant_create_time", "datetime NULL");
        virtualExecutor.execute(() -> {
            List<OciUser> ociUsers = userService.list(new LambdaQueryWrapper<OciUser>()
                    .isNull(OciUser::getTenantCreateTime)
                    .or()
                    .isNull(OciUser::getTenantName)
                    .or()
                    .eq(OciUser::getTenantName, "")
            );
            if (CollectionUtil.isNotEmpty(ociUsers)) {
                userService.updateBatchById(ociUsers.parallelStream().peek(x -> {
                    SysUserDTO sysUserDTO = sysService.getOciUser(x.getId());
                    try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
                        Tenancy tenancy = fetcher.getIdentityClient().getTenancy(GetTenancyRequest.builder()
                                .tenancyId(sysUserDTO.getOciCfg().getTenantId())
                                .build()).getTenancy();
                        x.setTenantName(tenancy.getName());
                        x.setTenantCreateTime(LocalDateTime.parse(fetcher.getRegisteredTime(), CommonUtils.DATETIME_FMT_NORM));
                    } catch (Exception e) {
                        log.error("更新配置：{} 失败", x.getUsername());
                    }
                }).collect(Collectors.toList()));
            }
        });
    }

    private void cleanAndRestartTask() {
        virtualExecutor.execute(() -> {
            Random random = new Random();
            Optional.ofNullable(createTaskService.list())
                    .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList)
                    .forEach(task -> {
                        // 随机延迟 5~10 秒
                        int delay = 5 + random.nextInt(6);
                        CREATE_INSTANCE_POOL.schedule(() -> {
                            if (task.getCreateNumbers() <= 0) {
                                createTaskService.removeById(task.getId());
                            } else {
                                OciUser ociUser = userService.getById(task.getUserId());
                                SysUserDTO sysUserDTO = SysUserDTO.builder()
                                        .ociCfg(SysUserDTO.OciCfg.builder()
                                                .userId(ociUser.getOciUserId())
                                                .tenantId(ociUser.getOciTenantId())
                                                .region(StrUtil.isBlank(task.getOciRegion()) ? ociUser.getOciRegion() : task.getOciRegion())
                                                .fingerprint(ociUser.getOciFingerprint())
                                                .privateKeyPath(ociUser.getOciKeyPath())
                                                .build())
                                        .taskId(task.getId())
                                        .username(ociUser.getUsername())
                                        .ocpus(task.getOcpus())
                                        .memory(task.getMemory())
                                        .disk(task.getDisk().equals(50) ? null : Long.valueOf(task.getDisk()))
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
                        }, delay, TimeUnit.SECONDS);
                    });
        });
    }

    private void initGenMfaPng() {
        virtualExecutor.execute(() -> {
            Optional.ofNullable(kvService.getOne(new LambdaQueryWrapper<OciKv>()
                    .eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode()))).ifPresent(mfa -> {
                String qrCodeURL = CommonUtils.generateQRCodeURL(mfa.getValue(), account, "oci-helper");
                CommonUtils.genQRPic(CommonUtils.MFA_QR_PNG_PATH, qrCodeURL);
            });
        });
    }

    private void saveVersion() {
        virtualExecutor.execute(() -> {
            String latestVersion = CommonUtils.getLatestVersion();
            OciKv oldVersion = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                    .eq(OciKv::getCode, SysCfgEnum.SYS_INFO_VERSION.getCode())
                    .eq(OciKv::getType, SysCfgTypeEnum.SYS_INFO.getCode()));
            if (null == oldVersion) {
                kvService.save(OciKv.builder()
                        .id(IdUtil.getSnowflake().nextIdStr())
                        .code(SysCfgEnum.SYS_INFO_VERSION.getCode())
                        .type(SysCfgTypeEnum.SYS_INFO.getCode())
                        .value(latestVersion)
                        .build());
            }
        });

    }

    private void startInform() {
        String latestVersion = CommonUtils.getLatestVersion();
        String nowVersion = kvService.getObj(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_INFO_VERSION.getCode())
                .eq(OciKv::getType, SysCfgTypeEnum.SYS_INFO.getCode())
                .select(OciKv::getValue), String::valueOf);
        log.info(String.format("【oci-helper】服务启动成功~ 当前版本：%s 最新版本：%s", nowVersion, latestVersion));
        sysService.sendMessage(String.format("【oci-helper】服务启动成功🎉🎉\n\n当前版本：%s\n最新版本：%s\n发送 /start 操作机器人🤖\n放货通知频道：https://t.me/oci_helper", nowVersion, latestVersion));
    }

    public static void pushVersionUpdateMsg(IOciKvService kvService, ISysService sysService) {
        String taskId = CacheConstant.PREFIX_PUSH_VERSION_UPDATE_MSG;

        addTask(taskId, () -> {
            OciKv evun = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                    .eq(OciKv::getCode, SysCfgEnum.ENABLED_VERSION_UPDATE_NOTIFICATIONS.getCode()));
            if (null != evun && evun.getValue().equals(EnableEnum.OFF.getCode())) {
                return;
            }
            String latest = CommonUtils.getLatestVersion();
            String now = kvService.getObj(new LambdaQueryWrapper<OciKv>()
                    .eq(OciKv::getCode, SysCfgEnum.SYS_INFO_VERSION.getCode())
                    .eq(OciKv::getType, SysCfgTypeEnum.SYS_INFO.getCode())
                    .select(OciKv::getValue), String::valueOf);
            if (StrUtil.isBlank(latest)) {
                return;
            }
            if (!now.equals(latest)) {
                log.warn(String.format("【oci-helper】版本更新啦！！！当前版本：%s 最新版本：%s", now, latest));
                if (!isPushedLatestVersion) {
                    sysService.sendMessage(String.format("🔔【oci-helper】版本更新啦！！！\n\n当前版本：%s\n最新版本：%s\n一键脚本：%s\n\n更新内容：\n%s",
                            now, latest,
                            "bash <(wget -qO- https://github.com/Yohann0617/oci-helper/releases/latest/download/sh_oci-helper_install.sh)",
                            CommonUtils.getLatestVersionBody()));
                    isPushedLatestVersion = true;
                }
            }
        }, 0, 1, TimeUnit.DAYS);

        addTask(taskId + "_push", () -> {
            OciKv evun = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                    .eq(OciKv::getCode, SysCfgEnum.ENABLED_VERSION_UPDATE_NOTIFICATIONS.getCode()));
            if (null != evun && evun.getValue().equals(EnableEnum.OFF.getCode())) {
                return;
            }
            isPushedLatestVersion = false;
        }, 12, 12, TimeUnit.HOURS);
    }

    private void dailyBroadcastTask() {
        OciKv edb = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.ENABLE_DAILY_BROADCAST.getCode()));
        OciKv dbc = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.DAILY_BROADCAST_CRON.getCode()));
        if (null != edb && edb.getValue().equals(EnableEnum.OFF.getCode())) {
            return;
        }

        ScheduledFuture<?> scheduled = taskScheduler.schedule(() -> {
            String message = "【每日播报】\n" +
                    "\n" +
                    "\uD83D\uDD58 时间：\t%s\n" +
                    "\uD83D\uDD11 总API配置数：\t%s\n" +
                    "❌ 失效API配置数：\t%s\n" +
                    "⚠\uFE0F 失效的API配置：\t\n- %s\n" +
                    "\uD83D\uDECE 正在执行的开机任务：\n" +
                    "%s\n";
            List<String> ids = userService.listObjs(new LambdaQueryWrapper<OciUser>()
                    .isNotNull(OciUser::getId)
                    .select(OciUser::getId), String::valueOf);

            CompletableFuture<List<String>> fails = CompletableFuture.supplyAsync(() -> {
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
            }, virtualExecutor);

            CompletableFuture<String> task = CompletableFuture.supplyAsync(() -> {
                List<OciCreateTask> ociCreateTaskList = createTaskService.list();
                if (ociCreateTaskList.isEmpty()) {
                    return "无";
                }
                String template = "[%s] [%s] [%s] [%s核/%sGB/%sGB] [%s台] [%s] [%s次]";
                return ociCreateTaskList.parallelStream().map(x -> {
                    OciUser ociUser = userService.getById(x.getUserId());
                    Long counts = (Long) TEMP_MAP.get(CommonUtils.CREATE_COUNTS_PREFIX + x.getId());
                    return String.format(template, ociUser.getUsername(), ociUser.getOciRegion(), x.getArchitecture(),
                            x.getOcpus().longValue(), x.getMemory().longValue(), x.getDisk(), x.getCreateNumbers(),
                            CommonUtils.getTimeDifference(x.getCreateTime()), counts == null ? "0" : counts);
                }).collect(Collectors.joining("\n"));
            }, virtualExecutor);

            CompletableFuture.allOf(fails, task).join();

            sysService.sendMessage(String.format(message,
                    LocalDateTime.now().format(CommonUtils.DATETIME_FMT_NORM),
                    CollectionUtil.isEmpty(ids) ? 0 : ids.size(),
                    fails.join().size(),
                    String.join("\n- ", fails.join()),
                    task.join()
            ));
        }, new CronTrigger(null == dbc ? CacheConstant.TASK_CRON : dbc.getValue()));

        TASK_MAP.put(CacheConstant.DAILY_BROADCAST_TASK_ID, scheduled);
    }

    private void supportOciUnknownRegionTask() {
        virtualExecutor.execute(() -> {
            Arrays.stream(OciUnSupportRegionEnum.values()).parallel()
                    .forEach(x -> {
                        try {
                            Region.fromRegionId(x.getRegionId());
                        } catch (Exception exception) {
                            Region.register(x.getRegionId(), x.getRealm(), x.getRegionCode());
                            log.info("support new region: [{}] successfully", x.getRegionId());
                        }
                    });
        });
    }

    private void initMapData() {
        virtualExecutor.execute(() -> {
            String jsonStr = HttpUtil.get(String.format("https://ipapi.co/json"));
            JSONObject json = JSONUtil.parseObj(jsonStr);
            IpData ipData = new IpData();
            ipData.setId(IdUtil.getSnowflakeNextIdStr());
            ipData.setIp(json.getStr("ip"));
            ipData.setCountry(json.getStr("country"));
            ipData.setArea(json.getStr("region"));
            ipData.setCity(json.getStr("city"));
            ipData.setOrg(json.getStr("org"));
            ipData.setAsn(json.getStr("asn"));
            ipData.setLat(Double.valueOf(json.getStr("latitude")));
            ipData.setLng(Double.valueOf(json.getStr("longitude")));
            List<IpData> ipDataList = ipDataService.list(new LambdaQueryWrapper<IpData>()
                    .eq(IpData::getIp, json.getStr("ip")));
            if (CollectionUtil.isNotEmpty(ipDataList)) {
                ipDataService.remove(new LambdaQueryWrapper<IpData>().eq(IpData::getIp, json.getStr("ip")));
            }
            ipDataService.save(ipData);
            log.info("新增地图IP数据：{} 成功", ipData.getIp());
        });
    }
}
