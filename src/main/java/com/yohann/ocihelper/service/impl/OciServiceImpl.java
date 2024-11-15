package com.yohann.ocihelper.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.Vnic;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciCreateTask;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.bean.params.*;
import com.yohann.ocihelper.bean.response.CreateTaskRsp;
import com.yohann.ocihelper.bean.response.OciCfgDetailsRsp;
import com.yohann.ocihelper.bean.response.OciUserListRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.enums.ArchitectureEnum;
import com.yohann.ocihelper.enums.MessageTypeEnum;
import com.yohann.ocihelper.enums.OciCfgEnum;
import com.yohann.ocihelper.enums.OperationSystemEnum;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.mapper.OciCreateTaskMapper;
import com.yohann.ocihelper.service.IInstanceService;
import com.yohann.ocihelper.service.IOciCreateTaskService;
import com.yohann.ocihelper.service.IOciService;
import com.yohann.ocihelper.service.IOciUserService;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.MessageServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import com.yohann.ocihelper.mapper.OciUserMapper;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * OciServiceImpl
 * </p >
 *
 * @author yohann
 * @since 2024/11/12 11:16
 */
@Slf4j
@Service
public class OciServiceImpl implements IOciService {

    @Resource
    private IInstanceService instanceService;
    @Resource
    private IOciUserService userService;
    @Resource
    private IOciCreateTaskService createTaskService;
    @Resource
    private MessageServiceFactory messageServiceFactory;
    @Resource
    private OciUserMapper userMapper;
    @Resource
    private OciCreateTaskMapper createTaskMapper;

    @Value("${web.account}")
    private String account;
    @Value("${web.password}")
    private String password;
    @Value("${oci-cfg.key-dir-path}")
    private String keyDirPath;

    public final static ScheduledThreadPoolExecutor CREATE_INSTANCE_POOL = new ScheduledThreadPoolExecutor(
            10,
            ThreadFactoryBuilder.create().setNamePrefix("oci-create-").build());
    public final static Map<String, Object> TEMP_MAP = new ConcurrentHashMap<>();

    private static final String CHANGE_IP_MESSAGE_TEMPLATE =
            "🎉 用户：%s 更换公共IP成功 🎉\n" +
                    "---------------------------\n" +
                    "时间： %s\n" +
                    "区域： %s\n" +
                    "实例： %s\n" +
                    "新的公网IP： %s";

    public static void execCreate(OracleInstanceFetcher fetcher, IInstanceService instanceService) {
        instanceService.createInstance(fetcher);
    }

    private OracleInstanceFetcher getOciFetcher(OciUser ociUser) {
        return new OracleInstanceFetcher(SysUserDTO.builder()
                .ociCfg(SysUserDTO.OciCfg.builder()
                        .userId(ociUser.getOciUserId())
                        .fingerprint(ociUser.getOciFingerprint())
                        .tenantId(ociUser.getOciTenantId())
                        .region(ociUser.getOciRegion())
                        .privateKeyPath(ociUser.getOciKeyPath())
                        .build())
                .build());
    }

    @Override
    public String login(LoginParams params) {
        if (!params.getAccount().equals(account) || !params.getPassword().equals(password)) {
            throw new OciException(-1, "账号或密码不正确");
        }
        Map<String, Object> payload = new HashMap<>(1);
        payload.put("account", CommonUtils.getMD5(account));
        return CommonUtils.genToken(payload, password);
    }

    @Override
    public Page<OciUserListRsp> userPage(GetOciUserListParams params) {
        long offset = (params.getCurrentPage() - 1) * params.getPageSize();
        List<OciUserListRsp> list = userMapper.userPage(offset, params.getPageSize(), params.getKeyword(), params.getIsEnableCreate());
        Long total = userMapper.userPageTotal(params.getKeyword(), params.getIsEnableCreate());
        return CommonUtils.buildPage(list, params.getPageSize(), params.getCurrentPage(), total);
    }

    @Override
    public void addCfg(AddCfgParams params) {
        Map<String, String> ociCfgMap = CommonUtils.getOciCfgFromStr(params.getOciCfgStr());
        OciUser ociUser = OciUser.builder()
                .id(IdUtil.randomUUID())
                .username(params.getUsername())
                .ociTenantId(ociCfgMap.get(OciCfgEnum.OCI_CFG_TENANT_ID.getType()))
                .ociUserId(ociCfgMap.get(OciCfgEnum.OCI_CFG_USER_ID.getType()))
                .ociFingerprint(ociCfgMap.get(OciCfgEnum.OCI_CFG_FINGERPRINT.getType()))
                .ociRegion(ociCfgMap.get(OciCfgEnum.OCI_CFG_REGION.getType()))
                .ociKeyPath(keyDirPath + File.separator + ociCfgMap.get(OciCfgEnum.OCI_CFG_KEY_FILE.getType()))
                .build();
        try {
            OracleInstanceFetcher ociFetcher = getOciFetcher(ociUser);
            ociFetcher.listInstances();
        } catch (Exception e) {
            throw new OciException(-1, "配置不生效，请检查密钥与配置项是否准确无误");
        }
        userService.save(ociUser);
    }

    @Override
    public void removeCfg(IdListParams params) {
        params.getIdList().forEach(id -> {
            if (createTaskService.count(new LambdaQueryWrapper<OciCreateTask>().eq(OciCreateTask::getUserId, id)) > 0) {
                throw new OciException(-1, "配置：" + userService.getById(id).getUsername() + " 存在开机任务，无法删除，请先停止开机任务");
            }
        });
        userService.removeBatchByIds(params.getIdList());
    }

    @Override
    public void createInstance(CreateInstanceParams params) {
        String taskId = IdUtil.randomUUID();
        OciUser ociUser = userService.getById(params.getUserId());
        OracleInstanceFetcher fetcher = new OracleInstanceFetcher(SysUserDTO.builder()
                .ociCfg(SysUserDTO.OciCfg.builder()
                        .userId(ociUser.getOciUserId())
                        .tenantId(ociUser.getOciTenantId())
                        .region(ociUser.getOciRegion())
                        .fingerprint(ociUser.getOciFingerprint())
                        .privateKeyPath(ociUser.getOciKeyPath())
                        .build())
                .taskId(taskId)
                .username(ociUser.getUsername())
                .ocpus(Float.parseFloat(params.getOcpus()))
                .memory(Float.parseFloat(params.getMemory()))
                .disk(Long.valueOf(params.getDisk()))
                .architecture(params.getArchitecture())
                .interval(Long.valueOf(params.getInterval()))
                .createNumbers(params.getCreateNumbers())
                .operationSystem(params.getOperationSystem())
                .rootPassword(params.getRootPassword())
                .build());
        createTaskService.save(OciCreateTask.builder()
                .id(taskId)
                .userId(params.getUserId())
                .ocpus(Float.parseFloat(params.getOcpus()))
                .memory(Float.parseFloat(params.getMemory()))
                .disk(Integer.valueOf(params.getDisk()))
                .architecture(params.getArchitecture())
                .interval(params.getInterval())
                .createNumbers(params.getCreateNumbers())
                .operationSystem(params.getOperationSystem())
                .rootPassword(params.getRootPassword())
                .operationSystem(params.getOperationSystem())
                .build());
        CREATE_INSTANCE_POOL.scheduleWithFixedDelay(() -> execCreate(fetcher, instanceService),
                0, params.getInterval(), TimeUnit.SECONDS);
    }

    @Override
    public OciCfgDetailsRsp details(IdParams params) {
        OciUser ociUser = userService.getById(params.getId());
        OracleInstanceFetcher fetcher = new OracleInstanceFetcher(SysUserDTO.builder()
                .ociCfg(SysUserDTO.OciCfg.builder()
                        .userId(ociUser.getOciUserId())
                        .tenantId(ociUser.getOciTenantId())
                        .region(ociUser.getOciRegion())
                        .fingerprint(ociUser.getOciFingerprint())
                        .privateKeyPath(ociUser.getOciKeyPath())
                        .build())
                .build());
        return new OciCfgDetailsRsp(Optional.ofNullable(fetcher.listInstances())
                .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
                .map(x -> {
                    OciCfgDetailsRsp.InstanceInfo info = new OciCfgDetailsRsp.InstanceInfo();
                    info.setOcId(x.getId());
                    info.setRegion(x.getRegion());
                    info.setName(x.getDisplayName());
                    info.setShape(x.getShape());
                    info.setPublicIp(fetcher.listInstanceIPs(x.getId()).stream()
                            .map(Vnic::getPublicIp)
                            .collect(Collectors.toList()));
                    info.setEnableChangeIp(TEMP_MAP.get(x.getId()) != null ? 1 : 0);
                    return info;
                }).collect(Collectors.toList()));
    }

    @Override
    public void changeIp(ChangeIpParams params) {
        params.getCidrList().forEach(cidr -> {
            if (!CommonUtils.isValidCidr(cidr)) {
                throw new OciException(-1, "无效的CIDR网段：" + cidr);
            }
        });

        TEMP_MAP.put(params.getInstanceId(), params);
        OciUser ociUser = userService.getById(params.getOciCfgId());
        OracleInstanceFetcher fetcher = new OracleInstanceFetcher(SysUserDTO.builder()
                .ociCfg(SysUserDTO.OciCfg.builder()
                        .userId(ociUser.getOciUserId())
                        .tenantId(ociUser.getOciTenantId())
                        .region(ociUser.getOciRegion())
                        .fingerprint(ociUser.getOciFingerprint())
                        .privateKeyPath(ociUser.getOciKeyPath())
                        .build())
                .username(ociUser.getUsername())
                .build());
        Instance instance = fetcher.getInstanceById(params.getInstanceId());
        CompletableFuture.runAsync(() -> {
            String publicIp = instanceService.changeInstancePublicIp(fetcher, instance, params.getCidrList());
            TEMP_MAP.remove(params.getInstanceId());
            try {
                String message = String.format(CHANGE_IP_MESSAGE_TEMPLATE,
                        ociUser.getUsername(),
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)),
                        ociUser.getOciRegion(), instance.getDisplayName(), publicIp);
                messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_TELEGRAM).sendMessage(message);
            } catch (Exception e) {
                log.error("【开机任务】用户：[{}] ，区域：[{}] ，实例：[{}] 更换公共IP成功，新的实例IP：{} ，但是消息发送失败",
                        ociUser.getUsername(), ociUser.getOciRegion(),
                        instance.getDisplayName(), publicIp);
            }
        });
    }

    @Override
    public void stopCreate(StopCreateParams params) {
        createTaskService.remove(new LambdaQueryWrapper<OciCreateTask>().eq(OciCreateTask::getUserId, params.getUserId()));
    }

    @Override
    public void stopChangeIp(StopChangeIpParams params) {
        TEMP_MAP.remove(params.getInstanceId());
    }

    @Override
    public Page<CreateTaskRsp> createTaskPage(CreateTaskPageParams params) {
        long offset = (params.getCurrentPage() - 1) * params.getPageSize();
        List<CreateTaskRsp> list = createTaskMapper.createTaskPage(offset, params.getPageSize(), params.getKeyword(), params.getArchitecture());
        Long total = createTaskMapper.createTaskPageTotal(params.getKeyword(), params.getArchitecture());
        return CommonUtils.buildPage(list, params.getPageSize(), params.getCurrentPage(), total);
    }

    @Override
    public void stopCreateBatch(IdListParams params) {
        createTaskService.removeBatchByIds(params.getIdList());
    }

    @Override
    public void createInstanceBatch(CreateInstanceBatchParams params) {
        params.getUserIds().stream().map(userId -> {
            CreateInstanceParams instanceParams = new CreateInstanceParams();
            BeanUtils.copyProperties(params.getInstanceInfo(), instanceParams);
            instanceParams.setUserId(userId);
            return instanceParams;
        }).collect(Collectors.toList()).parallelStream().forEach(this::createInstance);
    }
}
