package com.yohann.ocihelper.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciKv;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.bean.params.sys.*;
import com.yohann.ocihelper.bean.response.sys.GetSysCfgRsp;
import com.yohann.ocihelper.enums.MessageTypeEnum;
import com.yohann.ocihelper.enums.SysCfgEnum;
import com.yohann.ocihelper.enums.SysCfgTypeEnum;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.mapper.OciKvMapper;
import com.yohann.ocihelper.service.*;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.MessageServiceFactory;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.yohann.ocihelper.service.impl.OciServiceImpl.addTask;
import static com.yohann.ocihelper.service.impl.OciServiceImpl.execCreate;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.service.impl
 * @className: ISysServiceImpl
 * @author: Yohann
 * @date: 2024/11/30 17:09
 */
@Service
@Slf4j
public class ISysServiceImpl implements ISysService {

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
    private IOciCreateTaskService createTaskService;
    @Resource
    @Lazy
    private IInstanceService instanceService;
    @Resource
    private HttpServletResponse response;
    @Resource
    private OciKvMapper kvMapper;

    @Override
    public void sendMessage(String message) {
        messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_DING_DING).sendMessage(message);
        messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_TELEGRAM).sendMessage(message);
    }

    @Override
    public String login(LoginParams params) {
        if (getEnableMfa()) {
            if (params.getMfaCode() == null) {
                throw new OciException(-1, "验证码不能为空");
            }
            OciKv mfa = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                    .eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode()));
            if (!CommonUtils.verifyMfaCode(mfa.getValue(), params.getMfaCode())) {
                throw new OciException(-1, "无效的验证码");
            }
        }
        if (!params.getAccount().equals(account) || !params.getPassword().equals(password)) {
            throw new OciException(-1, "账号或密码不正确");
        }
        Map<String, Object> payload = new HashMap<>(1);
        payload.put("account", CommonUtils.getMD5(account));
        return CommonUtils.genToken(payload, password);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSysCfg(UpdateSysCfgParams params) {
        kvService.remove(new LambdaQueryWrapper<OciKv>().eq(OciKv::getType, SysCfgTypeEnum.SYS_INIT_CFG.getCode()));
        kvService.saveBatch(SysCfgEnum.getCodeListByType(SysCfgTypeEnum.SYS_INIT_CFG).parallelStream()
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
                        default:
                            break;
                    }
                    return ociKv;
                }).collect(Collectors.toList()));
        if (params.isEnableMfa()) {
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
    }

    @Override
    public GetSysCfgRsp getSysCfg() {
        GetSysCfgRsp rsp = new GetSysCfgRsp();
        rsp.setDingToken(getCfgValue(SysCfgEnum.SYS_DING_BOT_TOKEN));
        rsp.setDingSecret(getCfgValue(SysCfgEnum.SYS_DING_BOT_SECRET));
        rsp.setTgChatId(getCfgValue(SysCfgEnum.SYS_TG_CHAT_ID));
        rsp.setTgBotToken(getCfgValue(SysCfgEnum.SYS_TG_BOT_TOKEN));
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
                ServletUtil.write(response, bufferedInputStream,
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
                            log.info("restore table:{},size:{}", tableName,list.size());
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
            initGenMfaPng();
            cleanAndRestartTask();
        }
    }

    private String getCfgValue(SysCfgEnum sysCfgEnum) {
        OciKv cfg = kvService.getOne(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, sysCfgEnum.getCode()));
        return cfg == null ? null : cfg.getValue();
    }

    private void cleanAndRestartTask() {
        Optional.ofNullable(createTaskService.list())
                .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
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
                                        execCreate(sysUserDTO, instanceService, createTaskService),
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

}
