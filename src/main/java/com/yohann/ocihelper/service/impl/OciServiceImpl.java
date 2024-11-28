package com.yohann.ocihelper.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.Vnic;
import com.yohann.ocihelper.bean.Tuple2;
import com.yohann.ocihelper.bean.dto.InstanceDetailDTO;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciCreateTask;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.bean.params.*;
import com.yohann.ocihelper.bean.response.CreateTaskRsp;
import com.yohann.ocihelper.bean.response.OciCfgDetailsRsp;
import com.yohann.ocihelper.bean.response.OciUserListRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.enums.InstanceActionEnum;
import com.yohann.ocihelper.enums.MessageTypeEnum;
import com.yohann.ocihelper.enums.OciCfgEnum;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
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

    public final static Map<String, Object> TEMP_MAP = new ConcurrentHashMap<>();
    public final static Map<String, ScheduledFuture<?>> TASK_MAP = new ConcurrentHashMap<>();
    public final static ScheduledThreadPoolExecutor CREATE_INSTANCE_POOL = new ScheduledThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            ThreadFactoryBuilder.create().setNamePrefix("oci-task-").build());

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
        List<OciUser> ociUserList = userService.list(new LambdaQueryWrapper<OciUser>().eq(OciUser::getUsername, params.getUsername()));
        if (ociUserList.size() != 0) {
            throw new OciException(-1, "当前配置名称已存在");
        }
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
        SysUserDTO sysUserDTO = SysUserDTO.builder()
                .ociCfg(SysUserDTO.OciCfg.builder()
                        .userId(ociUser.getOciUserId())
                        .fingerprint(ociUser.getOciFingerprint())
                        .tenantId(ociUser.getOciTenantId())
                        .region(ociUser.getOciRegion())
                        .privateKeyPath(ociUser.getOciKeyPath())
                        .build())
                .build();
        try (OracleInstanceFetcher ociFetcher = new OracleInstanceFetcher(sysUserDTO)) {
            ociFetcher.listInstances();
        } catch (Exception e) {
            log.error("配置：[{}] ，区域：[{}] ，不生效，错误信息：[{}]",
                    ociUser.getUsername(), ociUser.getOciRegion(), e.getLocalizedMessage());
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
        OciCreateTask ociCreateTask = OciCreateTask.builder()
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
                .build();
        createTaskService.save(ociCreateTask);
        SysUserDTO sysUserDTO = SysUserDTO.builder()
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
                .build();
        addTask(CommonUtils.CREATE_TASK_PREFIX + taskId, () ->
                        execCreate(sysUserDTO, instanceService, createTaskService),
                0, params.getInterval(), TimeUnit.SECONDS);
        String beginCreateMsg = String.format(CommonUtils.BEGIN_CREATE_MESSAGE_TEMPLATE,
                ociUser.getUsername(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)),
                ociUser.getOciRegion(),
                params.getArchitecture(),
                Float.parseFloat(params.getOcpus()),
                Float.parseFloat(params.getMemory()),
                Long.valueOf(params.getDisk()),
                params.getCreateNumbers(),
                params.getRootPassword());
        messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_DING_DING).sendMessage(beginCreateMsg);
        messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_TELEGRAM).sendMessage(beginCreateMsg);
    }

    @Override
    public OciCfgDetailsRsp details(IdParams params) {
        OciUser ociUser = userService.getById(params.getId());
        SysUserDTO sysUserDTO = SysUserDTO.builder()
                .ociCfg(SysUserDTO.OciCfg.builder()
                        .userId(ociUser.getOciUserId())
                        .tenantId(ociUser.getOciTenantId())
                        .region(ociUser.getOciRegion())
                        .fingerprint(ociUser.getOciFingerprint())
                        .privateKeyPath(ociUser.getOciKeyPath())
                        .build())
                .build();
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO);) {
            OciCfgDetailsRsp rsp = new OciCfgDetailsRsp();
            BeanUtils.copyProperties(sysUserDTO.getOciCfg(), rsp);
            rsp.setInstanceList(Optional.ofNullable(fetcher.listInstances())
                    .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
                    .map(x -> fetcher.getInstanceInfo(x.getId()))
                    .collect(Collectors.toList()));
            return rsp;
        } catch (Exception e) {
            throw new OciException(-1, "获取实例信息失败");
        }
    }

    @Override
    public void changeIp(ChangeIpParams params) {
        params.getCidrList().forEach(cidr -> {
            if (!CommonUtils.isValidCidr(cidr)) {
                throw new OciException(-1, "无效的CIDR网段：" + cidr);
            }
        });

        SysUserDTO sysUserDTO = getOciUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            Instance instance = fetcher.getInstanceById(params.getInstanceId());
            String currentIp = fetcher.listInstanceIPs(params.getInstanceId()).stream()
                    .map(Vnic::getPublicIp)
                    .collect(Collectors.toList()).get(0);
            String message = String.format(CommonUtils.BEGIN_CHANGE_IP_MESSAGE_TEMPLATE,
                    sysUserDTO.getUsername(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)),
                    sysUserDTO.getOciCfg().getRegion(), instance.getDisplayName(), currentIp);
            log.info("【更换公共IP】用户：[{}] ，区域：[{}] ，实例：[{}] ，当前公网IP：[{}] 开始执行更换公网IP任务...",
                    sysUserDTO.getUsername(),
                    sysUserDTO.getOciCfg().getRegion(),
                    instance.getDisplayName(), currentIp);
            messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_DING_DING).sendMessage(message);
            try {
                messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_TELEGRAM).sendMessage(message);
            } catch (Exception e) {
                log.error("【更换公共IP】用户：[{}] ，区域：[{}] ，实例：[{}] 开始执行更换IP任务，但是TG消息发送失败",
                        sysUserDTO.getUsername(),
                        sysUserDTO.getOciCfg().getRegion(),
                        instance.getDisplayName());
            }
        } catch (Exception e) {
            throw new OciException(-1, "获取实例信息失败");
        }

        addTask(CommonUtils.CHANGE_IP_TASK_PREFIX + params.getInstanceId(), () -> execChange(
                params.getInstanceId(),
                sysUserDTO,
                params.getCidrList(),
                instanceService,
                60), 0, 60, TimeUnit.SECONDS);
    }

    @Override
    public void stopCreate(StopCreateParams params) {
        List<String> taskIds = createTaskService.listObjs(new LambdaQueryWrapper<OciCreateTask>()
                .eq(OciCreateTask::getUserId, params.getUserId())
                .select(OciCreateTask::getId), String::valueOf);
        if (CollectionUtil.isNotEmpty(taskIds)) {
            taskIds.parallelStream().forEach(x -> TEMP_MAP.remove(CommonUtils.CREATE_COUNTS_PREFIX + x));
        }
        createTaskService.remove(new LambdaQueryWrapper<OciCreateTask>().eq(OciCreateTask::getUserId, params.getUserId()));
        taskIds.parallelStream().forEach(taskId -> stopTask(CommonUtils.CREATE_TASK_PREFIX + taskId));
    }

    @Override
    public void stopChangeIp(StopChangeIpParams params) {
        stopTask(CommonUtils.CHANGE_IP_TASK_PREFIX + params.getInstanceId());
        TEMP_MAP.remove(CommonUtils.CHANGE_IP_ERROR_COUNTS_PREFIX + params.getInstanceId());
    }

    @Override
    public Page<CreateTaskRsp> createTaskPage(CreateTaskPageParams params) {
        long offset = (params.getCurrentPage() - 1) * params.getPageSize();
        List<CreateTaskRsp> list = createTaskMapper.createTaskPage(offset, params.getPageSize(), params.getKeyword(), params.getArchitecture());
        Long total = createTaskMapper.createTaskPageTotal(params.getKeyword(), params.getArchitecture());
        list.parallelStream().forEach(x -> {
            Long counts = (Long) TEMP_MAP.get(CommonUtils.CREATE_COUNTS_PREFIX + x.getId());
            x.setCounts(counts == null ? "0" : String.valueOf(counts));
        });
        return CommonUtils.buildPage(list, params.getPageSize(), params.getCurrentPage(), total);
    }

    @Override
    public void stopCreateBatch(IdListParams params) {
        createTaskService.removeBatchByIds(params.getIdList());
        params.getIdList().parallelStream().forEach(x -> TEMP_MAP.remove(CommonUtils.CREATE_COUNTS_PREFIX + x));
        params.getIdList().parallelStream().forEach(taskId -> stopTask(CommonUtils.CREATE_TASK_PREFIX + taskId));
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

    @Override
    public void uploadCfg(UploadCfgParams params) {
        params.getFileList().forEach(x -> {
            if (!x.getOriginalFilename().contains(".ini") && !x.getOriginalFilename().contains(".txt")) {
                throw new OciException(-1, "文件必须是.txt或者.ini的文本文件");
            }
        });
        Set<String> seenUsernames = new HashSet<>();
        List<OciUser> ociUserList = params.getFileList().parallelStream()
                .map(file -> {
                    try {
                        String read = IoUtil.read(file.getInputStream(), StandardCharsets.UTF_8);
                        return CommonUtils.parseConfigContent(read);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList()).stream() // Convert to a single stream
                .flatMap(Collection::stream).parallel()
                .peek(ociUser -> {
                    if (!seenUsernames.add(ociUser.getUsername())) {
                        log.error("名称：{} 重复，添加配置失败", ociUser.getUsername());
                        throw new OciException(-1, "名称: " + ociUser.getUsername() + " 重复，添加配置失败");
                    }
                    ociUser.setId(IdUtil.randomUUID());
                    ociUser.setOciKeyPath(keyDirPath + File.separator + ociUser.getOciKeyPath());
                    SysUserDTO sysUserDTO = SysUserDTO.builder()
                            .ociCfg(SysUserDTO.OciCfg.builder()
                                    .userId(ociUser.getOciUserId())
                                    .fingerprint(ociUser.getOciFingerprint())
                                    .tenantId(ociUser.getOciTenantId())
                                    .region(ociUser.getOciRegion())
                                    .privateKeyPath(ociUser.getOciKeyPath())
                                    .build())
                            .build();
                    try (OracleInstanceFetcher ociFetcher = new OracleInstanceFetcher(sysUserDTO)) {
                        ociFetcher.listInstances();
                    } catch (Exception e) {
                        log.error("配置：[{}] ，区域：[{}] 不生效，请检查密钥与配置项是否准确无误，错误信息：{}",
                                ociUser.getUsername(), ociUser.getOciRegion(), e.getLocalizedMessage());
                        throw new OciException(-1, "配置：" + ociUser.getUsername() + " 不生效，请检查密钥与配置项是否准确无误");
                    }
                })
                .collect(Collectors.toList());
        userService.saveBatch(ociUserList);
    }

    @Override
    public void updateInstanceState(UpdateInstanceStateParams params) {
        SysUserDTO sysUserDTO = getOciUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            fetcher.updateInstanceState(params.getInstanceId(), InstanceActionEnum.getActionEnum(params.getAction()));
        } catch (Exception e) {
            log.error("用户：[{}] ，区域：[{}] 更新实例状态失败，错误详情：[{}]",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), e.getLocalizedMessage());
            throw new OciException(-1, "更新实例状态失败");
        }
    }

    @Override
    public void terminateInstance(TerminateInstanceParams params) {
        String code = (String) TEMP_MAP.get(CommonUtils.TERMINATE_INSTANCE_PREFIX + params.getInstanceId());
        if (!params.getCaptcha().equals(code)) {
            throw new OciException(-1, "无效的验证码");
        }

        SysUserDTO sysUserDTO = getOciUser(params.getOciCfgId());
        CompletableFuture.runAsync(() -> {
            try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
                fetcher.terminateInstance(params.getInstanceId(), params.getPreserveBootVolume().equals(1), false);
                String message = String.format(CommonUtils.TERMINATE_INSTANCE_MESSAGE_TEMPLATE,
                        sysUserDTO.getUsername(),
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)),
                        sysUserDTO.getOciCfg().getRegion());
                messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_DING_DING).sendMessage(message);
                try {
                    messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_TELEGRAM).sendMessage(message);
                } catch (Exception e) {
                    log.error("【终止实例】用户：[{}] ，区域：[{}] 正在执行终止实例任务，但是TG消息发送失败",
                            sysUserDTO.getUsername(),
                            sysUserDTO.getOciCfg().getRegion());
                }
            } catch (Exception e) {
                log.error("用户：[{}] ，区域：[{}] 终止实例失败，错误详情：[{}]",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), e.getLocalizedMessage());
                throw new OciException(-1, "终止实例失败");
            }
        });
        TEMP_MAP.remove(CommonUtils.TERMINATE_INSTANCE_PREFIX + params.getInstanceId());
    }

    @Override
    public void sendCaptcha(SendCaptchaParams params) {
        SysUserDTO sysUserDTO = getOciUser(params.getOciCfgId());
        String verificationCode = RandomUtil.randomString(6);
        TEMP_MAP.put(CommonUtils.TERMINATE_INSTANCE_PREFIX + params.getInstanceId(), verificationCode);
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            OciCfgDetailsRsp.InstanceInfo instanceInfo = fetcher.getInstanceInfo(params.getInstanceId());
            String message = String.format(CommonUtils.TERMINATE_INSTANCE_CODE_MESSAGE_TEMPLATE,
                    sysUserDTO.getUsername(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)),
                    sysUserDTO.getOciCfg().getRegion(),
                    instanceInfo.getName(), instanceInfo.getShape(),
                    verificationCode);
            messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_DING_DING).sendMessage(message);
            try {
                messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_TELEGRAM).sendMessage(message);
            } catch (Exception e) {
                log.error("【终止实例】用户：[{}] ，区域：[{}] 正在执行终止实例前发送验证码任务，但是TG消息发送失败",
                        sysUserDTO.getUsername(),
                        sysUserDTO.getOciCfg().getRegion());
            }
        } catch (Exception e) {
            throw new OciException(-1, "发送验证码失败");
        }
    }

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

    public static void addTask(String taskId, Runnable task, long initialDelay, long period, TimeUnit timeUnit) {
        ScheduledFuture<?> future = CREATE_INSTANCE_POOL.scheduleWithFixedDelay(task, initialDelay, period, timeUnit);
        TASK_MAP.put(taskId, future);
    }

    public static void addAtFixedRateTask(String taskId, Runnable task, long initialDelay, long period, TimeUnit timeUnit) {
        ScheduledFuture<?> future = CREATE_INSTANCE_POOL.scheduleAtFixedRate(task, initialDelay, period, timeUnit);
        TASK_MAP.put(taskId, future);
    }

    public static void stopTask(String taskId) {
        ScheduledFuture<?> future = TASK_MAP.get(taskId);
        if (future != null) {
            future.cancel(false);
            TASK_MAP.remove(taskId);
        }
    }

    public static void execCreate(
            SysUserDTO sysUserDTO,
            IInstanceService instanceService,
            IOciCreateTaskService createTaskService) {

        if (createTaskService.getById(sysUserDTO.getTaskId()) == null) {
            log.warn("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] 任务终止......",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                    sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers());
            TEMP_MAP.remove(CommonUtils.CREATE_COUNTS_PREFIX + sysUserDTO.getTaskId());
            stopTask(CommonUtils.CREATE_TASK_PREFIX + sysUserDTO.getTaskId());
//            throw new OciException(-1, "任务终止");
        }

        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {

            List<InstanceDetailDTO> createInstanceList = instanceService.createInstance(fetcher).getCreateInstanceList();
            long successCounts = createInstanceList.stream().filter(InstanceDetailDTO::isSuccess).count();
            long outCounts = createInstanceList.stream().filter(InstanceDetailDTO::isOut).count();
            long leftCreateNum = sysUserDTO.getCreateNumbers() - successCounts;

            if (sysUserDTO.getCreateNumbers() == outCounts) {
                TEMP_MAP.remove(CommonUtils.CREATE_COUNTS_PREFIX + sysUserDTO.getTaskId());
                stopTask(CommonUtils.CREATE_TASK_PREFIX + sysUserDTO.getTaskId());
                createTaskService.remove(new LambdaQueryWrapper<OciCreateTask>().eq(OciCreateTask::getId, sysUserDTO.getTaskId()));
                log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] 因异常而终止任务......",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                        sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers());
            }

            if (sysUserDTO.getCreateNumbers() == successCounts || leftCreateNum == 0) {
                TEMP_MAP.remove(CommonUtils.CREATE_COUNTS_PREFIX + sysUserDTO.getTaskId());
                stopTask(CommonUtils.CREATE_TASK_PREFIX + sysUserDTO.getTaskId());
                createTaskService.remove(new LambdaQueryWrapper<OciCreateTask>().eq(OciCreateTask::getId, sysUserDTO.getTaskId()));
                log.warn("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] 任务结束......",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                        sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers());
            }

            if (leftCreateNum > 0) {
                createTaskService.update(new LambdaUpdateWrapper<OciCreateTask>()
                        .eq(OciCreateTask::getId, sysUserDTO.getTaskId())
                        .set(OciCreateTask::getCreateNumbers, leftCreateNum));
                sysUserDTO.setCreateNumbers((int) leftCreateNum);
            }
        } catch (Exception e) {
            log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] 发生了异常：{}",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                    sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers(), e.getLocalizedMessage());
//            TEMP_MAP.remove(CommonUtils.CREATE_COUNTS_PREFIX + sysUserDTO.getTaskId());
//            stopTask(CommonUtils.CREATE_TASK_PREFIX + sysUserDTO.getTaskId());
//            createTaskService.remove(new LambdaQueryWrapper<OciCreateTask>().eq(OciCreateTask::getId, sysUserDTO.getTaskId()));
        }
    }

    public void execChange(String instanceId,
                           SysUserDTO sysUserDTO,
                           List<String> cidrList,
                           IInstanceService instanceService,
                           int randomIntInterval) {
        if (CollectionUtil.isEmpty(cidrList)) {
            Tuple2<String, Instance> tuple2 = instanceService.changeInstancePublicIp(instanceId, sysUserDTO, cidrList);
            sendChangeIpMsg(
                    sysUserDTO.getUsername(),
                    sysUserDTO.getOciCfg().getRegion(),
                    tuple2.getSecond().getDisplayName(),
                    tuple2.getFirst()
            );
            stopTask(CommonUtils.CHANGE_IP_TASK_PREFIX + instanceId);
            TEMP_MAP.remove(CommonUtils.CHANGE_IP_ERROR_COUNTS_PREFIX + instanceId);
            return;
        }
        Tuple2<String, Instance> tuple2 = instanceService.changeInstancePublicIp(instanceId, sysUserDTO, cidrList);
        if (tuple2.getFirst() == null || tuple2.getSecond() == null) {
            Long currentCount = (Long) TEMP_MAP.compute(
                    CommonUtils.CHANGE_IP_ERROR_COUNTS_PREFIX + instanceId,
                    (key, value) -> value == null ? 1L : Long.parseLong(String.valueOf(value)) + 1
            );
            if (currentCount > 5) {
                log.error("【更换公共IP】用户：[{}] ，区域：[{}] ，实例：[{}] ，执行更换IP任务失败次数达到5次，任务终止",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), tuple2.getSecond().getDisplayName());
                stopTask(CommonUtils.CHANGE_IP_TASK_PREFIX + instanceId);
                TEMP_MAP.remove(CommonUtils.CHANGE_IP_ERROR_COUNTS_PREFIX + instanceId);
            }
            return;
        }
        String publicIp = tuple2.getFirst();
        String instanceName = tuple2.getSecond().getDisplayName();
        if (!CommonUtils.isIpInCidrList(tuple2.getFirst(), cidrList)) {
            log.warn("【更换公共IP】用户：[{}] ，区域：[{}] ，实例：[{}] ，获取到的IP：{} 不在给定的 CIDR 网段中，{} 秒后将继续更换公共IP...",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), instanceName,
                    publicIp, randomIntInterval);
            TEMP_MAP.remove(CommonUtils.CHANGE_IP_ERROR_COUNTS_PREFIX + instanceId);
        } else {
            sendChangeIpMsg(sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), instanceName, publicIp);
            stopTask(CommonUtils.CHANGE_IP_TASK_PREFIX + instanceId);
            TEMP_MAP.remove(CommonUtils.CHANGE_IP_ERROR_COUNTS_PREFIX + instanceId);
        }
    }

    private void sendChangeIpMsg(String username, String region, String instanceName, String publicIp) {
        log.info("✔✔✔【更换公共IP】用户：[{}] ，区域：[{}] ，实例：[{}] ，更换公共IP成功，新的公共IP地址：{} ✔✔✔",
                username, region, instanceName,
                publicIp);
        String message = String.format(CommonUtils.CHANGE_IP_MESSAGE_TEMPLATE,
                username,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)),
                region, instanceName, publicIp);
        messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_DING_DING).sendMessage(message);
        try {
            messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_TELEGRAM).sendMessage(message);
        } catch (Exception e) {
            log.error("【开机任务】用户：[{}] ，区域：[{}] ，实例：[{}] 更换公共IP成功，新的实例IP：{} ，但是TG消息发送失败",
                    username, region,
                    instanceName, publicIp);
        }
    }
}
