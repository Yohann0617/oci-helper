package com.yohann.ocihelper.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.system.SystemUtil;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.yohann.ocihelper.bean.constant.CacheConstant;
import com.yohann.ocihelper.bean.dto.GoogleLoginConfigDTO;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.IpData;
import com.yohann.ocihelper.bean.entity.OciCreateTask;
import com.yohann.ocihelper.bean.entity.OciKv;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.bean.params.sys.*;
import com.yohann.ocihelper.bean.response.sys.GetGlanceRsp;
import com.yohann.ocihelper.bean.response.sys.GetSysCfgRsp;
import com.yohann.ocihelper.bean.response.sys.LoginRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.enums.EnableEnum;
import com.yohann.ocihelper.enums.MessageTypeEnum;
import com.yohann.ocihelper.enums.SysCfgEnum;
import com.yohann.ocihelper.enums.SysCfgTypeEnum;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.mapper.OciKvMapper;
import com.yohann.ocihelper.service.*;
import com.yohann.ocihelper.telegram.TgBot;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import com.yohann.ocihelper.utils.MessageServiceFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

import jakarta.annotation.Resource;

import java.io.*;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.yohann.ocihelper.service.impl.OciServiceImpl.*;
import static com.yohann.ocihelper.task.OciTask.botsApplication;
import static com.yohann.ocihelper.task.OciTask.pushVersionUpdateMsg;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.service.impl
 * @className: ISysServiceImpl
 * @author: Yohann
 * @date: 2024/11/30 17:09
 */
@Service
@Slf4j
public class SysServiceImpl implements ISysService {

    @Value("${web.account}")
    private String account;
    @Value("${web.password}")
    private String password;

    @Resource
    private MessageServiceFactory messageServiceFactory;
    @Resource
    private IOciUserService userService;
    @Resource
    private IOciKvService kvService;
    @Resource
    @Lazy
    private IIpDataService ipDataService;
    @Resource
    private IOciCreateTaskService createTaskService;
    @Resource
    @Lazy
    private IInstanceService instanceService;
    @Resource
    private HttpServletRequest request;
    @Resource
    private HttpServletResponse response;
    @Resource
    private OciKvMapper kvMapper;
    @Resource
    private TaskScheduler taskScheduler;
    @Resource
    private ExecutorService virtualExecutor;
    @Resource
    private CustomExpiryGuavaCache<String, Object> customCache;

    @Override
    public void sendMessage(String message) {
        virtualExecutor.execute(() -> messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_DING_DING).sendMessage(message));
        virtualExecutor.execute(() -> messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_TELEGRAM).sendMessage(message));
    }

    @Override
    public LoginRsp login(LoginParams params) {
        String clientIp = CommonUtils.getClientIP(request);
        if (getEnableMfa()) {
            if (params.getMfaCode() == null) {
                log.error("请求IP：{} 登录失败，如果不是本人操作，可能存在被攻击的风险", clientIp);
                sendMessage(String.format("请求IP：%s 登录失败，如果不是本人操作，可能存在被攻击的风险", clientIp));
                throw new OciException(-1, "验证码不能为空");
            }
            OciKv mfa = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                    .eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode()));
            if (!CommonUtils.verifyMfaCode(mfa.getValue(), params.getMfaCode())) {
                log.error("请求IP：{} 登录失败，如果不是本人操作，可能存在被攻击的风险", clientIp);
                sendMessage(String.format("请求IP：%s 登录失败，如果不是本人操作，可能存在被攻击的风险", clientIp));
                throw new OciException(-1, "无效的验证码");
            }
        }
        if (!params.getAccount().equals(account) || !params.getPassword().equals(password)) {
            log.error("请求IP：{} 登录失败，如果不是本人操作，可能存在被攻击的风险", clientIp);
            sendMessage(String.format("请求IP：%s 登录失败，如果不是本人操作，可能存在被攻击的风险", clientIp));
            throw new OciException(-1, "账号或密码不正确");
        }
        Map<String, Object> payload = new HashMap<>(1);
        payload.put("account", CommonUtils.getMD5(account));
        String token = CommonUtils.genToken(payload, password);

        String latestVersion = CommonUtils.getLatestVersion();
        String currentVersion = kvService.getObj(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_INFO_VERSION.getCode())
                .eq(OciKv::getType, SysCfgTypeEnum.SYS_INFO.getCode())
                .select(OciKv::getValue), String::valueOf);
        sendMessage(String.format("请求IP：%s 登录成功，时间：%s", clientIp, LocalDateTime.now().format(CommonUtils.DATETIME_FMT_NORM)));
        LoginRsp rsp = new LoginRsp();
        rsp.setToken(token);
        rsp.setCurrentVersion(currentVersion);
        rsp.setLatestVersion(latestVersion);
        return rsp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSysCfg(UpdateSysCfgParams params) {
        kvService.remove(new LambdaQueryWrapper<OciKv>().eq(OciKv::getType, SysCfgTypeEnum.SYS_INIT_CFG.getCode()));
        kvService.saveBatch(SysCfgEnum.getCodeListByType(SysCfgTypeEnum.SYS_INIT_CFG).stream()
                .map(x -> {
                    OciKv ociKv = new OciKv();
                    ociKv.setId(IdUtil.getSnowflakeNextIdStr());
                    ociKv.setCode(x.getCode());
                    ociKv.setType(SysCfgTypeEnum.SYS_INIT_CFG.getCode());
                    switch (x) {
                        case SYS_TG_BOT_TOKEN:
                            ociKv.setValue(params.getTgBotToken());
                            break;
                        case SYS_TG_CHAT_ID:
                            ociKv.setValue(params.getTgChatId());
                            break;
                        case SYS_DING_BOT_TOKEN:
                            ociKv.setValue(params.getDingToken());
                            break;
                        case SYS_DING_BOT_SECRET:
                            ociKv.setValue(params.getDingSecret());
                            break;
                        case ENABLE_DAILY_BROADCAST:
                            ociKv.setValue(params.getEnableDailyBroadcast().toString());
                            break;
                        case DAILY_BROADCAST_CRON:
                            ociKv.setValue(params.getDailyBroadcastCron());
                            break;
                        case ENABLED_VERSION_UPDATE_NOTIFICATIONS:
                            ociKv.setValue(params.getEnableVersionInform().toString());
                            break;
                        case SILICONFLOW_AI_API:
                            ociKv.setValue(params.getGjAiApi());
                            customCache.remove(SysCfgEnum.SILICONFLOW_AI_API.getCode());
                            break;
                        case BOOT_BROADCAST_TOKEN:
                            ociKv.setValue(params.getBootBroadcastToken());
                            break;
                        case GOOGLE_ONE_CLICK_LOGIN:
                            GoogleLoginConfigDTO googleConfig = new GoogleLoginConfigDTO();
                            googleConfig.setEnabled(params.getEnableGoogleLogin() != null ? params.getEnableGoogleLogin() : false);
                            googleConfig.setClientId(params.getGoogleClientId());
                            googleConfig.setAllowedEmailSuffixes(params.getGoogleAllowedEmailSuffixes());
                            ociKv.setValue(JSONUtil.toJsonStr(googleConfig));
                            break;
                        default:
                            break;
                    }
                    return ociKv;
                }).collect(Collectors.toList()));
        if (params.getEnableMfa()) {
            OciKv mfaInDb = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                    .eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode()));
            if (mfaInDb == null) {
                String secretKey = CommonUtils.generateSecretKey();
                OciKv mfa = new OciKv();
                mfa.setId(IdUtil.getSnowflakeNextIdStr());
                mfa.setCode(SysCfgEnum.SYS_MFA_SECRET.getCode());
                mfa.setValue(secretKey);
                mfa.setType(SysCfgTypeEnum.SYS_MFA_CFG.getCode());
                String qrCodeURL = CommonUtils.generateQRCodeURL(secretKey, account, "oci-helper");
                CommonUtils.genQRPic(CommonUtils.MFA_QR_PNG_PATH, qrCodeURL);
                kvService.save(mfa);
            }
        } else {
            kvService.remove(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode()));
            FileUtil.del(CommonUtils.MFA_QR_PNG_PATH);
        }

        startTgBot(params.getTgBotToken(), params.getTgChatId());

        stopTask(CacheConstant.PREFIX_PUSH_VERSION_UPDATE_MSG);
        stopTask(CacheConstant.PREFIX_PUSH_VERSION_UPDATE_MSG + "_push");
        if (params.getEnableVersionInform()) {
            pushVersionUpdateMsg(kvService, this);
        }

        ScheduledFuture<?> scheduledFuture = TASK_MAP.get(CacheConstant.DAILY_BROADCAST_TASK_ID);
        if (null != scheduledFuture) {
            scheduledFuture.cancel(true);
        }
        if (params.getEnableDailyBroadcast()) {
            TASK_MAP.put(CacheConstant.DAILY_BROADCAST_TASK_ID, taskScheduler.schedule(this::dailyBroadcastTask,
                    new CronTrigger(StrUtil.isBlank(params.getDailyBroadcastCron()) ? CacheConstant.TASK_CRON : params.getDailyBroadcastCron())));
        }
    }

    @Override
    public GetSysCfgRsp getSysCfg() {
        GetSysCfgRsp rsp = new GetSysCfgRsp();
        rsp.setDingToken(getCfgValue(SysCfgEnum.SYS_DING_BOT_TOKEN));
        rsp.setDingSecret(getCfgValue(SysCfgEnum.SYS_DING_BOT_SECRET));
        rsp.setTgChatId(getCfgValue(SysCfgEnum.SYS_TG_CHAT_ID));
        rsp.setTgBotToken(getCfgValue(SysCfgEnum.SYS_TG_BOT_TOKEN));
        String edbValue = getCfgValue(SysCfgEnum.ENABLE_DAILY_BROADCAST);
        rsp.setEnableDailyBroadcast(Boolean.valueOf(null == edbValue ? EnableEnum.ON.getCode() : edbValue));
        String dbcValue = getCfgValue(SysCfgEnum.DAILY_BROADCAST_CRON);
        rsp.setDailyBroadcastCron(null == dbcValue ? CacheConstant.TASK_CRON : dbcValue);
        String evunValue = getCfgValue(SysCfgEnum.ENABLED_VERSION_UPDATE_NOTIFICATIONS);
        rsp.setEnableVersionInform(Boolean.valueOf(null == evunValue ? EnableEnum.ON.getCode() : evunValue));
        rsp.setGjAiApi(getCfgValue(SysCfgEnum.SILICONFLOW_AI_API));
        rsp.setBootBroadcastToken(getCfgValue(SysCfgEnum.BOOT_BROADCAST_TOKEN));

        // Parse Google login configuration from JSON
        String googleLoginJson = getCfgValue(SysCfgEnum.GOOGLE_ONE_CLICK_LOGIN);
        if (StrUtil.isNotBlank(googleLoginJson)) {
            try {
                GoogleLoginConfigDTO googleConfig = JSONUtil.toBean(googleLoginJson, GoogleLoginConfigDTO.class);
                rsp.setEnableGoogleLogin(googleConfig.getEnabled());
                rsp.setGoogleClientId(googleConfig.getClientId());
                rsp.setGoogleAllowedEmailSuffixes(googleConfig.getAllowedEmailSuffixes());
            } catch (Exception e) {
                log.error("解析Google登录配置失败：{}", e.getMessage());
                rsp.setEnableGoogleLogin(false);
                rsp.setGoogleClientId(null);
                rsp.setGoogleAllowedEmailSuffixes(null);
            }
        } else {
            rsp.setEnableGoogleLogin(false);
            rsp.setGoogleClientId(null);
            rsp.setGoogleAllowedEmailSuffixes(null);
        }

        OciKv mfa = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode()));
        rsp.setEnableMfa(mfa != null);
        Optional.ofNullable(mfa).ifPresent(x -> {
            rsp.setMfaSecret(x.getValue());
            try (FileInputStream in = new FileInputStream(CommonUtils.MFA_QR_PNG_PATH);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                IoUtil.copy(in, out);
                rsp.setMfaQrData("data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray()));
            } catch (Exception e) {
                log.error("获取MFA二维码图片失败：{}", e.getLocalizedMessage());
            }
        });
        return rsp;
    }

    @Override
    public boolean getEnableMfa() {
        return kvService.getOne(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode())) != null;
    }

    @Override
    public void backup(BackupParams params) {
        if (params.isEnableEnc() && StrUtil.isBlank(params.getPassword())) {
            throw new OciException(-1, "密码不能为空");
        }
        File tempDir = null;
        File dataFile = null;
        File outEncZip = null;
        try {
            String basicDirPath = System.getProperty("user.dir") + File.separator;
            tempDir = FileUtil.mkdir(basicDirPath + "oci-helper-backup-" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern(DatePattern.PURE_DATETIME_PATTERN)));
            String keysDirPath = basicDirPath + "keys";
            FileUtil.copy(keysDirPath, tempDir.getAbsolutePath(), true);

            Map<String, IService> serviceMap = SpringUtil.getBeanFactory().getBeansOfType(IService.class);
            Map<String, List> listMap = serviceMap.entrySet().parallelStream()
                    .collect(Collectors.toMap(Map.Entry::getKey, (x) -> x.getValue().list()));
            String jsonStr = JSONUtil.toJsonStr(listMap);
            dataFile = FileUtil.touch(basicDirPath + "data.json");
            FileUtil.writeString(jsonStr, dataFile, Charset.defaultCharset());
            FileUtil.copy(dataFile, tempDir, true);

            outEncZip = FileUtil.touch(tempDir.getAbsolutePath() + ".zip");
            ZipFile zipFile = CommonUtils.zipFile(
                    params.isEnableEnc(),
                    tempDir.getAbsolutePath(),
                    params.getPassword(),
                    outEncZip.getAbsolutePath());

            response.setCharacterEncoding(CharsetUtil.UTF_8);
            try (BufferedInputStream bufferedInputStream = FileUtil.getInputStream(zipFile.getFile())) {
                CommonUtils.writeResponse(response, bufferedInputStream,
                        "application/octet-stream",
                        zipFile.getFile().getName());
            } catch (Exception e) {
                log.error("备份文件失败：{}", e.getLocalizedMessage());
                throw new OciException(-1, "备份文件失败");
            }
        } catch (Exception e) {
            log.error("备份文件失败：{}", e.getLocalizedMessage());
            throw new OciException(-1, "备份文件失败");
        } finally {
            FileUtil.del(tempDir);
            FileUtil.del(dataFile);
            FileUtil.del(outEncZip);
        }
    }

    @Override
    public String createBackupFile(BackupParams params) {
        if (params.isEnableEnc() && StrUtil.isBlank(params.getPassword())) {
            throw new OciException(-1, "密码不能为空");
        }
        File tempDir = null;
        File dataFile = null;
        File outEncZip = null;
        try {
            String basicDirPath = System.getProperty("user.dir") + File.separator;
            tempDir = FileUtil.mkdir(basicDirPath + "oci-helper-backup-" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern(DatePattern.PURE_DATETIME_PATTERN)));
            String keysDirPath = basicDirPath + "keys";
            FileUtil.copy(keysDirPath, tempDir.getAbsolutePath(), true);

            Map<String, IService> serviceMap = SpringUtil.getBeanFactory().getBeansOfType(IService.class);
            Map<String, List> listMap = serviceMap.entrySet().parallelStream()
                    .collect(Collectors.toMap(Map.Entry::getKey, (x) -> x.getValue().list()));
            String jsonStr = JSONUtil.toJsonStr(listMap);
            dataFile = FileUtil.touch(basicDirPath + "data.json");
            FileUtil.writeString(jsonStr, dataFile, Charset.defaultCharset());
            FileUtil.copy(dataFile, tempDir, true);

            outEncZip = FileUtil.touch(tempDir.getAbsolutePath() + ".zip");
            ZipFile zipFile = CommonUtils.zipFile(
                    params.isEnableEnc(),
                    tempDir.getAbsolutePath(),
                    params.getPassword(),
                    outEncZip.getAbsolutePath());

            // Return the zip file path instead of writing to response
            String backupFilePath = zipFile.getFile().getAbsolutePath();
            log.info("备份文件创建成功: {}", backupFilePath);

            // Don't delete the zip file, caller will handle it
            FileUtil.del(tempDir);
            FileUtil.del(dataFile);

            return backupFilePath;
        } catch (Exception e) {
            log.error("备份文件失败：{}", e.getLocalizedMessage());
            // Clean up on error
            FileUtil.del(tempDir);
            FileUtil.del(dataFile);
            FileUtil.del(outEncZip);
            throw new OciException(-1, "备份文件失败");
        }
    }

    @Override
    public void recover(RecoverParams params) {
        String basicDirPath = System.getProperty("user.dir") + File.separator;
        MultipartFile file = params.getFileList().get(0);
        File tempZip = FileUtil.createTempFile();
        File unzipDir = null;
        try (InputStream inputStream = file.getInputStream();
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();) {
            IoUtil.copy(inputStream, byteArrayOutputStream);

            FileUtil.writeBytes(byteArrayOutputStream.toByteArray(), tempZip);

            CommonUtils.unzipFile(basicDirPath, params.getEncryptionKey(), tempZip.getAbsolutePath());

            unzipDir = new File(basicDirPath + file.getOriginalFilename().replaceAll(".zip", ""));
            if (!unzipDir.exists()) {
                throw new OciException(-1, "解压失败");
            }

            for (File unzipFile : unzipDir.listFiles()) {
                if (unzipFile.isDirectory() && unzipFile.getName().contains("keys")) {
                    FileUtil.copyFilesFromDir(unzipFile, new File(basicDirPath + "keys"), false);
                }
                if (unzipFile.isFile() && unzipFile.getName().contains("data.json")) {
                    Map<String, IService> serviceMap = SpringUtil.getBeanFactory().getBeansOfType(IService.class);
                    List<String> impls = new ArrayList<>(serviceMap.keySet());
                    String readJsonStr = FileUtil.readUtf8String(unzipFile);
                    Map<String, List> map = JSONUtil.toBean(readJsonStr, Map.class);

                    impls.forEach(x -> {
                        List list = map.get(x);
                        if (null != list) {
                            list.forEach(obj -> {
                                try {
                                    String time = String.valueOf(BeanUtil.getFieldValue(obj, "tenantCreateTime"));
                                    Long timestamp = Long.valueOf(time);
                                    LocalDateTime localDateTime = Instant.ofEpochMilli(timestamp)
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDateTime();
                                    BeanUtil.setFieldValue(obj, "tenantCreateTime", localDateTime);
                                } catch (Exception ignored) {
                                }
                                try {
                                    String time = String.valueOf(BeanUtil.getFieldValue(obj, "createTime"));
                                    Long timestamp = Long.valueOf(time);
                                    LocalDateTime localDateTime = Instant.ofEpochMilli(timestamp)
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDateTime();
                                    BeanUtil.setFieldValue(obj, "createTime", localDateTime);
                                } catch (Exception ignored) {
                                }
                            });

                            IService service = serviceMap.get(x);
                            Class entityClass = service.getEntityClass();
                            String simpleName = entityClass.getSimpleName();
                            TableName annotation = (TableName) entityClass.getAnnotation(TableName.class);
                            String tableName = annotation == null ? StrUtil.toUnderlineCase(simpleName) : annotation.value();
                            log.info("clear table:{}", tableName);
                            kvMapper.removeAllData(tableName);
                            log.info("restore table:{},size:{}", tableName, list.size());
                            service.saveBatch(list);
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("恢复数据失败：{}", e.getLocalizedMessage());
            throw new OciException(-1, "恢复数据失败");
        } finally {
            FileUtil.del(tempZip);
            FileUtil.del(unzipDir);
            virtualExecutor.execute(() -> {
                initGenMfaPng();
                cleanAndRestartTask();
            });
        }
    }

    @Override
    public void recoverFromFile(String backupFilePath, String password) {
        String basicDirPath = System.getProperty("user.dir") + File.separator;
        File tempZip = new File(backupFilePath);
        File unzipDir = null;

        if (!tempZip.exists()) {
            throw new OciException(-1, "备份文件不存在");
        }

        try {
            // 解压到临时目录
            String tempUnzipDir = basicDirPath + "temp_unzip_" + System.currentTimeMillis();
            new File(tempUnzipDir).mkdirs();

            CommonUtils.unzipFile(tempUnzipDir, password, tempZip.getAbsolutePath());

            // 查找解压后的备份目录（应该是 oci-helper-backup-* 格式）
            File tempUnzipDirFile = new File(tempUnzipDir);
            File[] subDirs = tempUnzipDirFile.listFiles(File::isDirectory);

            if (subDirs == null || subDirs.length == 0) {
                // 没有子目录，直接使用解压目录
                unzipDir = tempUnzipDirFile;
            } else {
                // 使用第一个子目录（应该是备份目录）
                unzipDir = subDirs[0];
            }

            if (!unzipDir.exists() || unzipDir.listFiles() == null || unzipDir.listFiles().length == 0) {
                throw new OciException(-1, "解压失败或备份文件为空");
            }

            log.info("备份文件解压成功: {}", unzipDir.getAbsolutePath());

            for (File unzipFile : unzipDir.listFiles()) {
                if (unzipFile.isDirectory() && unzipFile.getName().contains("keys")) {
                    FileUtil.copyFilesFromDir(unzipFile, new File(basicDirPath + "keys"), false);
                    log.info("恢复 keys 目录成功");
                }
                if (unzipFile.isFile() && unzipFile.getName().contains("data.json")) {
                    log.info("开始恢复数据库数据...");
                    Map<String, IService> serviceMap = SpringUtil.getBeanFactory().getBeansOfType(IService.class);
                    List<String> impls = new ArrayList<>(serviceMap.keySet());
                    String readJsonStr = FileUtil.readUtf8String(unzipFile);
                    Map<String, List> map = JSONUtil.toBean(readJsonStr, Map.class);

                    impls.forEach(x -> {
                        List list = map.get(x);
                        if (null != list) {
                            list.forEach(obj -> {
                                try {
                                    String time = String.valueOf(BeanUtil.getFieldValue(obj, "tenantCreateTime"));
                                    Long timestamp = Long.valueOf(time);
                                    LocalDateTime localDateTime = Instant.ofEpochMilli(timestamp)
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDateTime();
                                    BeanUtil.setFieldValue(obj, "tenantCreateTime", localDateTime);
                                } catch (Exception ignored) {
                                }
                                try {
                                    String time = String.valueOf(BeanUtil.getFieldValue(obj, "createTime"));
                                    Long timestamp = Long.valueOf(time);
                                    LocalDateTime localDateTime = Instant.ofEpochMilli(timestamp)
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDateTime();
                                    BeanUtil.setFieldValue(obj, "createTime", localDateTime);
                                } catch (Exception ignored) {
                                }
                            });

                            IService service = serviceMap.get(x);
                            Class entityClass = service.getEntityClass();
                            String simpleName = entityClass.getSimpleName();
                            TableName annotation = (TableName) entityClass.getAnnotation(TableName.class);
                            String tableName = annotation == null ? StrUtil.toUnderlineCase(simpleName) : annotation.value();
                            log.info("clear table:{}", tableName);
                            kvMapper.removeAllData(tableName);
                            log.info("restore table:{},size:{}", tableName, list.size());
                            service.saveBatch(list);
                        }
                    });
                    log.info("数据库数据恢复成功");
                }
            }

            log.info("数据恢复成功");
        } catch (Exception e) {
            e.printStackTrace();
            log.error("恢复数据失败：{}", e.getLocalizedMessage());
            throw new OciException(-1, "恢复数据失败");
        } finally {
            // 清理解压目录
            if (unzipDir != null) {
                try {
                    // 删除整个临时解压目录
                    File tempUnzipDirFile = new File(basicDirPath + "temp_unzip_" +
                            unzipDir.getParentFile().getName().replace("temp_unzip_", ""));
                    if (tempUnzipDirFile.exists()) {
                        FileUtil.del(tempUnzipDirFile);
                    } else {
                        FileUtil.del(unzipDir);
                    }
                } catch (Exception e) {
                    log.warn("删除临时目录失败", e);
                }
            }
            virtualExecutor.execute(() -> {
                initGenMfaPng();
                cleanAndRestartTask();
            });
        }
    }

    @Override
    public GetGlanceRsp glance() {
        GetGlanceRsp rsp = new GetGlanceRsp();
        List<String> ids = userService.listObjs(new LambdaQueryWrapper<OciUser>()
                .isNotNull(OciUser::getId)
                .select(OciUser::getId), String::valueOf);

        CompletableFuture<List<GetGlanceRsp.MapData>> mapDataFuture = CompletableFuture.supplyAsync(() -> Optional.ofNullable(ipDataService.list())
                .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
                .filter(ip -> ip.getLat() != null && ip.getLng() != null)
                .collect(Collectors.groupingBy(
                        ip -> new AbstractMap.SimpleEntry<>(ip.getLat(), ip.getLng()),
                        Collectors.toList()
                ))
                .entrySet().parallelStream()
                .map(entry -> {
                    GetGlanceRsp.MapData mapData = new GetGlanceRsp.MapData();
                    mapData.setCountry(entry.getValue().get(0).getCountry());
                    mapData.setArea(entry.getValue().get(0).getArea());
                    mapData.setOrg(entry.getValue().stream().map(IpData::getOrg).distinct().collect(Collectors.joining(",")));
                    mapData.setAsn(entry.getValue().stream().map(IpData::getAsn).distinct().collect(Collectors.joining(",")));
                    mapData.setLat(entry.getKey().getKey());
                    mapData.setLng(entry.getKey().getValue());
                    mapData.setCount(entry.getValue().size());
                    mapData.setCity(entry.getValue().get(0).getCity());
                    return mapData;
                })
                .collect(Collectors.toList()), virtualExecutor);

        CompletableFuture<String> tasksFuture = CompletableFuture.supplyAsync(() -> {
            List<String> userIds = createTaskService.listObjs(new LambdaQueryWrapper<OciCreateTask>()
                    .isNotNull(OciCreateTask::getId)
                    .select(OciCreateTask::getUserId), String::valueOf);
            return String.valueOf(userIds.size());
        }, virtualExecutor);

        CompletableFuture<String> regionsFuture = CompletableFuture.supplyAsync(() -> {
            if (CollectionUtil.isEmpty(ids)) {
                return "0";
            }

            return String.valueOf(userService.listObjs(new LambdaQueryWrapper<OciUser>()
                            .isNotNull(OciUser::getId)
                            .select(OciUser::getOciRegion), String::valueOf)
                    .stream().distinct().count());
        }, virtualExecutor);

        CompletableFuture<String> daysFuture = CompletableFuture.supplyAsync(() -> {
            long uptimeMillis = SystemUtil.getRuntimeMXBean().getUptime();
            return String.valueOf(uptimeMillis / (24 * 60 * 60 * 1000));
        }, virtualExecutor);

        CompletableFuture<String> currentVersionFuture = CompletableFuture.supplyAsync(() -> kvService.getObj(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_INFO_VERSION.getCode())
                .eq(OciKv::getType, SysCfgTypeEnum.SYS_INFO.getCode())
                .select(OciKv::getValue), String::valueOf), virtualExecutor);

        CompletableFuture.allOf(mapDataFuture, tasksFuture, regionsFuture, daysFuture, currentVersionFuture).join();

        try {
            rsp.setCities(mapDataFuture.get());
            rsp.setUsers(String.valueOf(ids.size()));
            rsp.setTasks(tasksFuture.get());
            rsp.setRegions(regionsFuture.get());
            rsp.setDays(daysFuture.get());
            rsp.setCurrentVersion(currentVersionFuture.get());
        } catch (Exception e) {
            log.error("获取系统信息失败", e);
            throw new OciException(-1, "Error while fetching glance data");
        }

        return rsp;
    }

    @Override
    public SysUserDTO getOciUser(String ociCfgId) {
        OciUser ociUser = userService.getById(ociCfgId);
        return SysUserDTO.builder()
                .ociCfg(SysUserDTO.OciCfg.builder()
                        .userId(ociUser.getOciUserId())
                        .tenantId(ociUser.getOciTenantId())
                        .region(ociUser.getOciRegion())
                        .fingerprint(ociUser.getOciFingerprint())
                        .privateKeyPath(ociUser.getOciKeyPath())
                        .build())
                .username(ociUser.getUsername())
                .build();
    }

    @Override
    public SysUserDTO getOciUser(String ociCfgId, String region, String compartmentId) {
        OciUser ociUser = userService.getById(ociCfgId);
        return SysUserDTO.builder()
                .ociCfg(SysUserDTO.OciCfg.builder()
                        .userId(ociUser.getOciUserId())
                        .tenantId(ociUser.getOciTenantId())
                        .region(StrUtil.isBlank(region) ? ociUser.getOciRegion() : region)
                        .fingerprint(ociUser.getOciFingerprint())
                        .privateKeyPath(ociUser.getOciKeyPath())
                        .compartmentId(compartmentId)
                        .build())
                .username(ociUser.getUsername())
                .build();
    }

    @Override
    public void checkMfaCode(String mfaCode) {
        OciKv mfa = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode()));
        if (!CommonUtils.verifyMfaCode(mfa.getValue(), Integer.parseInt(mfaCode))) {
            throw new OciException(-1, "无效的验证码");
        }
    }

    @Override
    public void updateVersion() {
        String latestVersion = CommonUtils.getLatestVersion();
        String currentVersion = kvService.getObj(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_INFO_VERSION.getCode())
                .eq(OciKv::getType, SysCfgTypeEnum.SYS_INFO.getCode())
                .select(OciKv::getValue), String::valueOf);
        if (latestVersion.equals(currentVersion)) {
            throw new OciException(-1, "当前已是最新版本，请返回主页并刷新页面查看");
        }
        List<String> command = List.of("/bin/sh", "-c", "echo trigger > /app/oci-helper/update_version_trigger.flag");
        Process process = RuntimeUtil.exec(command.toArray(new String[0]));

        int exitCode = 0;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            log.error("TG Bot error", e);
        }

        if (exitCode == 0) {
            log.info("Start the version update task...");
        } else {
            log.error("version update task exec error,exitCode:{}", exitCode);
        }
    }

    @Override
    public LoginRsp googleLogin(GoogleLoginParams params) {
        String clientIp = CommonUtils.getClientIP(request);
        try {
            // Get Google login configuration from database
            String googleLoginJson = getCfgValue(SysCfgEnum.GOOGLE_ONE_CLICK_LOGIN);
            if (StrUtil.isBlank(googleLoginJson)) {
                log.error("请求IP：{} Google登录失败，Google登录功能未配置", clientIp);
                throw new OciException(-1, "Google登录功能未配置");
            }

            GoogleLoginConfigDTO googleConfig = JSONUtil.toBean(googleLoginJson, GoogleLoginConfigDTO.class);
            if (googleConfig.getEnabled() == null || !googleConfig.getEnabled()) {
                log.error("请求IP：{} Google登录失败，Google登录功能未启用", clientIp);
                throw new OciException(-1, "Google登录功能未启用");
            }

            if (StrUtil.isBlank(googleConfig.getClientId())) {
                log.error("请求IP：{} Google登录失败，Google Client ID未配置", clientIp);
                throw new OciException(-1, "Google Client ID未配置");
            }

            // Verify the Google ID token
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleConfig.getClientId()))
                    .build();

            GoogleIdToken idToken = verifier.verify(params.getCredential());
            if (idToken == null) {
                log.error("请求IP：{} Google登录失败，无效的凭证", clientIp);
                sendMessage(String.format("请求IP：%s Google登录失败，无效的凭证，如果不是本人操作，可能存在被攻击的风险", clientIp));
                throw new OciException(-1, "无效的Google凭证");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            boolean emailVerified = payload.getEmailVerified();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");

            if (!emailVerified) {
                log.error("请求IP：{} Google登录失败，邮箱未验证", clientIp);
                throw new OciException(-1, "Google邮箱未验证");
            }

            // Validate email suffix if configured
            if (StrUtil.isNotBlank(googleConfig.getAllowedEmailSuffixes())) {
                String[] allowedSuffixes = googleConfig.getAllowedEmailSuffixes().split(",");
                boolean isAllowed = false;
                for (String suffix : allowedSuffixes) {
                    String trimmedSuffix = suffix.trim();
                    if (StrUtil.isNotBlank(trimmedSuffix) && email.toLowerCase().endsWith(trimmedSuffix.toLowerCase())) {
                        isAllowed = true;
                        break;
                    }
                }
                if (!isAllowed) {
                    log.error("请求IP：{} Google登录失败，邮箱 {} 不在允许的后缀列表中", clientIp, email);
                    sendMessage(String.format("请求IP：%s Google登录失败，邮箱 %s 不在允许的后缀列表中，如果不是本人操作，可能存在被攻击的风险", clientIp, email));
                    throw new OciException(-1, "该Google账号不在允许登录的账号列表中");
                }
            }

            // Generate JWT token
            Map<String, Object> tokenPayload = new HashMap<>(2);
            tokenPayload.put("account", CommonUtils.getMD5(email));
            tokenPayload.put("googleUser", true);
            String token = CommonUtils.genToken(tokenPayload, password);

            // Get version info
            String latestVersion = CommonUtils.getLatestVersion();
            String currentVersion = kvService.getObj(new LambdaQueryWrapper<OciKv>()
                    .eq(OciKv::getCode, SysCfgEnum.SYS_INFO_VERSION.getCode())
                    .eq(OciKv::getType, SysCfgTypeEnum.SYS_INFO.getCode())
                    .select(OciKv::getValue), String::valueOf);

            sendMessage(String.format("Google用户 [%s] 从IP：%s 登录成功，时间：%s",
                    email, clientIp, LocalDateTime.now().format(CommonUtils.DATETIME_FMT_NORM)));

            LoginRsp rsp = new LoginRsp();
            rsp.setToken(token);
            rsp.setCurrentVersion(currentVersion);
            rsp.setLatestVersion(latestVersion);
            return rsp;
        } catch (Exception e) {
            log.error("请求IP：{} Google登录失败，错误信息：{}", clientIp, e.getMessage(), e);
            sendMessage(String.format("请求IP：%s Google登录失败，如果不是本人操作，可能存在被攻击的风险", clientIp));
            if (e instanceof OciException) {
                throw (OciException) e;
            }
            throw new OciException(-1, "Google登录失败：" + e.getMessage());
        }
    }

    @Override
    public String getGoogleClientId() {
        String googleLoginJson = getCfgValue(SysCfgEnum.GOOGLE_ONE_CLICK_LOGIN);
        if (StrUtil.isBlank(googleLoginJson)) {
            return null;
        }

        try {
            GoogleLoginConfigDTO googleConfig = JSONUtil.toBean(googleLoginJson, GoogleLoginConfigDTO.class);
            // Only return client ID if Google login is enabled
            if (googleConfig.getEnabled() != null && googleConfig.getEnabled()) {
                return googleConfig.getClientId();
            }
            return null;
        } catch (Exception e) {
            log.error("解析Google登录配置失败：{}", e.getMessage());
            return null;
        }
    }

    private String getCfgValue(SysCfgEnum sysCfgEnum) {
        OciKv cfg = kvService.getOne(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, sysCfgEnum.getCode()));
        return cfg == null ? null : cfg.getValue();
    }

    private void cleanAndRestartTask() {
        Random random = new Random();
        Optional.ofNullable(createTaskService.list())
                .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).stream()
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
                                            .region(ociUser.getOciRegion())
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
                            stopTask(CommonUtils.CREATE_TASK_PREFIX + task.getId());
                            addTask(CommonUtils.CREATE_TASK_PREFIX + task.getId(), () ->
                                            execCreate(sysUserDTO, this, instanceService, createTaskService),
                                    0, task.getInterval(), TimeUnit.SECONDS);
                        }
                    }, delay, TimeUnit.SECONDS);
                });
    }

    private void initGenMfaPng() {
        Optional.ofNullable(kvService.getOne(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode()))).ifPresent(mfa -> {
            String qrCodeURL = CommonUtils.generateQRCodeURL(mfa.getValue(), account, "oci-helper");
            CommonUtils.genQRPic(CommonUtils.MFA_QR_PNG_PATH, qrCodeURL);
        });
    }

    private void dailyBroadcastTask() {
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
                SysUserDTO ociUser = this.getOciUser(id);
                try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(ociUser)) {
                    fetcher.getAvailabilityDomains();
                } catch (Exception e) {
                    return true;
                }
                return false;
            }).map(id -> this.getOciUser(id).getUsername()).collect(Collectors.toList());
        });

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
        });

        CompletableFuture.allOf(fails, task).join();

        this.sendMessage(String.format(message,
                LocalDateTime.now().format(CommonUtils.DATETIME_FMT_NORM),
                CollectionUtil.isEmpty(ids) ? 0 : ids.size(),
                fails.join().size(),
                String.join("\n- ", fails.join()),
                task.join()
        ));
    }

    private void startTgBot(String botToken, String chatId) {
        if (StrUtil.isBlank(botToken) || StrUtil.isBlank(chatId)) {
            if (null != botsApplication && botsApplication.isRunning()) {
                try {
                    botsApplication.close();
                } catch (Exception e) {
                    log.error("TG Bot Application close error", e);
                }
            }
        }
        virtualExecutor.execute(() -> {
            if (StrUtil.isNotBlank(botToken) && StrUtil.isNotBlank(chatId)) {
                if (null != botsApplication && botsApplication.isRunning()) {
                    try {
                        botsApplication.close();
                    } catch (Exception e) {
                        log.error("TG Bot Application close error", e);
                    }
                }
                botsApplication = new TelegramBotsLongPollingApplication();
                try {
                    botsApplication.registerBot(botToken, new TgBot(botToken, chatId));
                    Thread.currentThread().join();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
