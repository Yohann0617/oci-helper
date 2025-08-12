package com.yohann.ocihelper.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.*;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oracle.bmc.core.BlockstorageClient;
import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.model.*;
import com.oracle.bmc.core.requests.*;
import com.oracle.bmc.core.responses.AttachBootVolumeResponse;
import com.oracle.bmc.core.responses.CreateBootVolumeBackupResponse;
import com.oracle.bmc.core.responses.CreateBootVolumeResponse;
import com.oracle.bmc.identity.model.AvailabilityDomain;
import com.oracle.bmc.identity.model.Tenancy;
import com.oracle.bmc.identity.requests.GetTenancyRequest;
import com.oracle.bmc.model.BmcException;
import com.yohann.ocihelper.bean.Tuple2;
import com.yohann.ocihelper.bean.constant.CacheConstant;
import com.yohann.ocihelper.bean.dto.InstanceCfgDTO;
import com.yohann.ocihelper.bean.dto.InstanceDetailDTO;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.CfCfg;
import com.yohann.ocihelper.bean.entity.OciCreateTask;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.bean.params.*;
import com.yohann.ocihelper.bean.params.cf.OciAddCfDnsRecordsParams;
import com.yohann.ocihelper.bean.params.cf.RemoveCfDnsRecordsParams;
import com.yohann.ocihelper.bean.params.oci.cfg.*;
import com.yohann.ocihelper.bean.params.oci.instance.*;
import com.yohann.ocihelper.bean.params.oci.securityrule.ReleaseSecurityRuleParams;
import com.yohann.ocihelper.bean.params.oci.task.CreateTaskPageParams;
import com.yohann.ocihelper.bean.params.oci.task.StopChangeIpParams;
import com.yohann.ocihelper.bean.params.oci.task.StopCreateParams;
import com.yohann.ocihelper.bean.params.oci.volume.UpdateBootVolumeCfgParams;
import com.yohann.ocihelper.bean.response.oci.task.CreateTaskRsp;
import com.yohann.ocihelper.bean.response.oci.cfg.OciCfgDetailsRsp;
import com.yohann.ocihelper.bean.response.oci.cfg.OciUserListRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.enums.ArchitectureEnum;
import com.yohann.ocihelper.enums.ErrorEnum;
import com.yohann.ocihelper.enums.InstanceActionEnum;
import com.yohann.ocihelper.enums.OciCfgEnum;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.mapper.OciCreateTaskMapper;
import com.yohann.ocihelper.service.*;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import com.yohann.ocihelper.utils.OciConsoleUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import com.yohann.ocihelper.mapper.OciUserMapper;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.yohann.ocihelper.config.VirtualThreadConfig.VIRTUAL_EXECUTOR;

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
    private ICfCfgService cfCfgService;
    @Resource
    private ICfApiService cfApiService;
    @Resource
    private IOciUserService userService;
    @Resource
    private IOciCreateTaskService createTaskService;
    @Resource
    private ISysService sysService;
    @Resource
    private CustomExpiryGuavaCache<String, Object> customCache;
    @Resource
    private OciUserMapper userMapper;
    @Resource
    private OciCreateTaskMapper createTaskMapper;
    @Resource
    private ExecutorService virtualExecutor;

    @Value("${oci-cfg.key-dir-path}")
    private String keyDirPath;

    public final static Map<String, Object> TEMP_MAP = new ConcurrentHashMap<>();
    public final static Map<String, ScheduledFuture<?>> TASK_MAP = new ConcurrentHashMap<>();
    public final static ScheduledThreadPoolExecutor CREATE_INSTANCE_POOL = new ScheduledThreadPoolExecutor(
            Math.min(4, Runtime.getRuntime().availableProcessors()),
            ThreadFactoryBuilder.create().setNamePrefix("oci-task-").build());
    public final static Set<String> RUNNING_TASKS = ConcurrentHashMap.newKeySet();

    @Override
    public Page<OciUserListRsp> userPage(GetOciUserListParams params) {
        long offset = (params.getCurrentPage() - 1) * params.getPageSize();
        List<OciUserListRsp> list = userMapper.userPage(offset, params.getPageSize(), params.getKeyword(), params.getIsEnableCreate());
        Long total = userMapper.userPageTotal(params.getKeyword(), params.getIsEnableCreate());
        return CommonUtils.buildPage(list, params.getPageSize(), params.getCurrentPage(), total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addCfg(AddCfgParams params) {
        List<OciUser> ociUserList = userService.list(new LambdaQueryWrapper<OciUser>().eq(OciUser::getUsername, params.getUsername()));
        if (ociUserList.size() != 0) {
            throw new OciException(-1, "当前配置名称已存在");
        }

        String priKeyPath = keyDirPath + File.separator + params.getFile().getOriginalFilename();
        File priKey = FileUtil.touch(priKeyPath);
        try (InputStream inputStream = params.getFile().getInputStream();
             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(Files.newOutputStream(priKey.toPath()))) {
            IoUtil.copy(inputStream, bufferedOutputStream);
        } catch (Exception e) {
            throw new OciException(-1, "写入私钥文件失败");
        }

        Map<String, String> ociCfgMap = CommonUtils.getOciCfgFromStr(params.getOciCfgStr());
        OciUser ociUser = OciUser.builder()
                .id(IdUtil.randomUUID())
                .username(params.getUsername())
                .ociTenantId(ociCfgMap.get(OciCfgEnum.OCI_CFG_TENANT_ID.getType()))
                .ociUserId(ociCfgMap.get(OciCfgEnum.OCI_CFG_USER_ID.getType()))
                .ociFingerprint(ociCfgMap.get(OciCfgEnum.OCI_CFG_FINGERPRINT.getType()))
                .ociRegion(ociCfgMap.get(OciCfgEnum.OCI_CFG_REGION.getType()))
                .ociKeyPath(priKeyPath)
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
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            fetcher.getAvailabilityDomains();
            Tenancy tenancy = fetcher.getIdentityClient().getTenancy(GetTenancyRequest.builder()
                    .tenancyId(sysUserDTO.getOciCfg().getTenantId())
                    .build()).getTenancy();
            ociUser.setTenantName(tenancy.getName());
        } catch (Exception e) {
            log.error("配置：[{}] ，区域：[{}] ，不生效，错误信息：[{}]",
                    ociUser.getUsername(), ociUser.getOciRegion(), e.getLocalizedMessage());
            throw new OciException(-1, "配置不生效，请检查密钥与配置项是否准确无误");
        }
        userService.save(ociUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCfg(IdListParams params) {
        params.getIdList().forEach(id -> {
            if (createTaskService.count(new LambdaQueryWrapper<OciCreateTask>().eq(OciCreateTask::getUserId, id)) > 0) {
                throw new OciException(-1, "配置：" + userService.getById(id).getUsername() + " 存在开机任务，无法删除，请先停止开机任务");
            }
        });
        userService.removeBatchByIds(params.getIdList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
                        execCreate(sysUserDTO, sysService, instanceService, createTaskService),
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

        sysService.sendMessage(beginCreateMsg);
    }

    @Override
    public OciCfgDetailsRsp details(GetOciCfgDetailsParams params) {
        if (params.isCleanReLaunchDetails()) {
            customCache.remove(CacheConstant.PREFIX_INSTANCE_PAGE + params.getCfgId());
        }
        List<OciCfgDetailsRsp.InstanceInfo> instanceInfos =
                (List<OciCfgDetailsRsp.InstanceInfo>) customCache.get(CacheConstant.PREFIX_INSTANCE_PAGE + params.getCfgId());

        SysUserDTO sysUserDTO = getOciUser(params.getCfgId());
        OciCfgDetailsRsp rsp = new OciCfgDetailsRsp();
        BeanUtils.copyProperties(sysUserDTO.getOciCfg(), rsp);
        String privateKeyPath = rsp.getPrivateKeyPath();
        rsp.setPrivateKeyPath(privateKeyPath.substring(privateKeyPath.lastIndexOf(File.separator) + 1));

        if (ObjUtil.isEmpty(instanceInfos)) {
            try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO);) {
                rsp.setInstanceList(Optional.ofNullable(fetcher.listInstances())
                        .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
                        .map(x -> fetcher.getInstanceInfo(x.getId()))
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                log.error("获取实例信息失败", e);
                throw new OciException(-1, "获取实例信息失败");
            }
        } else {
            rsp.setInstanceList(instanceInfos);
        }

        customCache.put(CacheConstant.PREFIX_INSTANCE_PAGE + params.getCfgId(), rsp.getInstanceList(), 10 * 60 * 1000);

        rsp.setCfCfgList(Optional.ofNullable(cfCfgService.list())
                .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).stream()
                .map(x -> new OciCfgDetailsRsp.CfCfg(x.getId(), x.getDomain()))
                .collect(Collectors.toList()));

        return rsp;
    }

    @Override
    public void changeIp(ChangeIpParams params) {
        params.getCidrList().forEach(cidr -> {
            if (!CommonUtils.isValidCidr(cidr)) {
                throw new OciException(-1, "无效的CIDR网段：" + cidr);
            }
        });

        if (params.isChangeCfDns()) {
            if (StrUtil.isBlank(params.getSelectedDomainCfgId()) || StrUtil.isBlank(params.getDomainPrefix())) {
                throw new OciException(-1, "域名或域名前缀不能为空");
            }
        }

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
            sysService.sendMessage(message);
        } catch (Exception e) {
            throw new OciException(-1, "获取实例信息失败");
        }

        addTask(CommonUtils.CHANGE_IP_TASK_PREFIX + params.getInstanceId(), () -> execChange(
                params,
                sysUserDTO,
                instanceService,
                60), 0, 60, TimeUnit.SECONDS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stopCreate(StopCreateParams params) {
        List<String> taskIds = createTaskService.listObjs(new LambdaQueryWrapper<OciCreateTask>()
                .eq(OciCreateTask::getUserId, params.getUserId())
                .select(OciCreateTask::getId), String::valueOf);
        if (CollectionUtil.isNotEmpty(taskIds)) {
            taskIds.forEach(x -> TEMP_MAP.remove(CommonUtils.CREATE_COUNTS_PREFIX + x));
            taskIds.forEach(taskId -> stopTask(CommonUtils.CREATE_TASK_PREFIX + taskId));
        }
        createTaskService.remove(new LambdaQueryWrapper<OciCreateTask>().eq(OciCreateTask::getUserId, params.getUserId()));
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
            x.setOcpus(Double.valueOf(x.getOcpus()).longValue() + "");
            x.setMemory(Double.valueOf(x.getMemory()).longValue() + "");
        });
        return CommonUtils.buildPage(list, params.getPageSize(), params.getCurrentPage(), total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stopCreateBatch(IdListParams params) {
        createTaskService.removeBatchByIds(params.getIdList());
        params.getIdList().forEach(x -> TEMP_MAP.remove(CommonUtils.CREATE_COUNTS_PREFIX + x));
        params.getIdList().forEach(taskId -> stopTask(CommonUtils.CREATE_TASK_PREFIX + taskId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createInstanceBatch(CreateInstanceBatchParams params) {
        params.getUserIds().stream().map(userId -> {
            CreateInstanceParams instanceParams = new CreateInstanceParams();
            BeanUtils.copyProperties(params.getInstanceInfo(), instanceParams);
            instanceParams.setUserId(userId);
            return instanceParams;
        }).collect(Collectors.toList()).forEach(this::createInstance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
                .collect(Collectors.toList()).stream()
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
                        ociFetcher.getAvailabilityDomains();
                        Tenancy tenancy = ociFetcher.getIdentityClient().getTenancy(GetTenancyRequest.builder()
                                .tenancyId(sysUserDTO.getOciCfg().getTenantId())
                                .build()).getTenancy();
                        ociUser.setTenantName(tenancy.getName());
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
        String code = (String) customCache.get(CommonUtils.TERMINATE_INSTANCE_PREFIX + params.getInstanceId());
        if (!params.getCaptcha().equals(code)) {
            throw new OciException(-1, "无效的验证码");
        }

        stopTask(CommonUtils.CHANGE_IP_TASK_PREFIX + params.getInstanceId());
        SysUserDTO sysUserDTO = getOciUser(params.getOciCfgId());
        virtualExecutor.execute(() -> {
            try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
                fetcher.terminateInstance(params.getInstanceId(), params.getPreserveBootVolume().equals(1), params.getPreserveBootVolume().equals(1));
                String message = String.format(CommonUtils.TERMINATE_INSTANCE_MESSAGE_TEMPLATE,
                        sysUserDTO.getUsername(),
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)),
                        sysUserDTO.getOciCfg().getRegion());
                sysService.sendMessage(message);
            } catch (Exception e) {
                log.error("用户：[{}] ，区域：[{}] 终止实例失败，错误详情：[{}]",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), e.getLocalizedMessage());
                throw new OciException(-1, "终止实例失败");
            }
        });
        customCache.remove(CommonUtils.TERMINATE_INSTANCE_PREFIX + params.getInstanceId());
    }

    @Override
    public void sendCaptcha(SendCaptchaParams params) {
        SysUserDTO sysUserDTO = getOciUser(params.getOciCfgId());
        String verificationCode = RandomUtil.randomString(6);
        customCache.put(CommonUtils.TERMINATE_INSTANCE_PREFIX + params.getInstanceId(), verificationCode, 5 * 60 * 1000);
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            OciCfgDetailsRsp.InstanceInfo instanceInfo = fetcher.getInstanceInfo(params.getInstanceId());
            String message = String.format(CommonUtils.TERMINATE_INSTANCE_CODE_MESSAGE_TEMPLATE,
                    sysUserDTO.getUsername(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)),
                    sysUserDTO.getOciCfg().getRegion(),
                    instanceInfo.getName(), instanceInfo.getShape(),
                    verificationCode);
            log.info(message);
            sysService.sendMessage(message);
        } catch (Exception e) {
            throw new OciException(-1, "发送验证码失败");
        }
    }

    @Override
    public void releaseSecurityRule(ReleaseSecurityRuleParams params) {
        SysUserDTO sysUserDTO = getOciUser(params.getOciCfgId());
        instanceService.releaseSecurityRule(sysUserDTO);
    }

    @Override
    public InstanceCfgDTO getInstanceCfgInfo(GetInstanceCfgInfoParams params) {
        SysUserDTO sysUserDTO = getOciUser(params.getOciCfgId());
        return instanceService.getInstanceCfgInfo(sysUserDTO, params.getInstanceId());
    }

    @Override
    public void createIpv6(CreateIpv6Params params) {
        SysUserDTO sysUserDTO = getOciUser(params.getOciCfgId());
        instanceService.createIpv6(sysUserDTO, params.getInstanceId());
    }

    @Override
    public void updateInstanceName(UpdateInstanceNameParams params) {
        SysUserDTO sysUserDTO = getOciUser(params.getOciCfgId());
        instanceService.updateInstanceName(sysUserDTO, params.getInstanceId(), params.getName());
    }

    @Override
    public void updateInstanceCfg(UpdateInstanceCfgParams params) {
        SysUserDTO sysUserDTO = getOciUser(params.getOciCfgId());
        instanceService.updateInstanceCfg(sysUserDTO, params.getInstanceId(),
                Float.parseFloat(params.getOcpus()), Float.parseFloat(params.getMemory()));
    }

    @Override
    public void updateBootVolumeCfg(UpdateBootVolumeCfgParams params) {
        SysUserDTO sysUserDTO = getOciUser(params.getOciCfgId());
        instanceService.updateBootVolumeCfg(sysUserDTO, params.getInstanceId(),
                Long.parseLong(params.getBootVolumeSize()), Long.parseLong(params.getBootVolumeVpu()));
    }

    @Override
    public String checkAlive() {
        List<String> ids = userService.listObjs(new LambdaQueryWrapper<OciUser>()
                .isNotNull(OciUser::getId)
                .select(OciUser::getId), String::valueOf);
        if (CollectionUtil.isEmpty(ids)) {
            return null;
        }

        String rst = "总配置数：%s ，失效配置数：%s ，有效配置数：%s。\n 失效配置：\n%s";

        List<String> failNames = ids.parallelStream().filter(id -> {
            SysUserDTO ociUser = getOciUser(id);
            try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(ociUser)) {
                fetcher.getAvailabilityDomains();
            } catch (Exception e) {
                return true;
            }
            return false;
        }).map(id -> getOciUser(id).getUsername()).collect(Collectors.toList());

        sysService.sendMessage(String.format("【API测活结果】\n\n✅ 有效配置数：%s\n❌ 失效配置数：%s\n\uD83D\uDD11 总配置数：%s\n⚠\uFE0F 失效配置：\n%s",
                ids.size() - failNames.size(), failNames.size(), ids.size(), String.join("\n", failNames)));

        return String.format(rst, ids.size(), failNames.size(), ids.size() - failNames.size(), String.join(" , ", failNames));
    }

    @Override
    public void updateCfgName(UpdateCfgNameParams params) {
        Optional.ofNullable(userService.getOne(new LambdaQueryWrapper<OciUser>()
                .eq(OciUser::getUsername, params.getUpdateCfgName()))).ifPresent(user -> {
            if (!user.getId().equals(params.getCfgId())) {
                throw new OciException(-1, "配置名称：【" + params.getUpdateCfgName() + "】已存在");
            }
        });

        userService.update(new LambdaUpdateWrapper<OciUser>()
                .eq(OciUser::getId, params.getCfgId())
                .set(OciUser::getUsername, params.getUpdateCfgName()));
    }

    @Override
    public String startVnc(StartVncParams params) {
        SysUserDTO sysUserDTO = getOciUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            if (StrUtil.isNotBlank(params.getCompartmentId())) {
                fetcher.setCompartmentId(params.getCompartmentId());
            }

            String resStr = String.format("【%s】【%s】", sysUserDTO.getUsername(), fetcher.getInstanceById(params.getInstanceId()).getDisplayName());

            // 检查并释放 5900 端口
            try {
                String portCheckCmd = "lsof -i:5900 -t";
                String pid = RuntimeUtil.execForStr("sh", "-c", portCheckCmd).trim();
                if (StrUtil.isNotBlank(pid)) {
                    log.warn("Port 5900 is occupied by PID {}. Killing it.", pid);
                    RuntimeUtil.exec("kill", "-9", pid);
                }
            } catch (Exception e) {
                log.error("Failed to check/kill process on port 5900", e);
            }

            // 避免重复生成密钥
            File privateKey = new File("/root/.ssh/id_rsa");
            File publicKey = new File("/root/.ssh/id_rsa.pub");

            if (!privateKey.exists() || !publicKey.exists()) {
                // 构造命令：生成无密码 SSH 密钥
                ProcessBuilder builder = new ProcessBuilder(
                        "ssh-keygen",
                        "-t", "rsa",
                        "-b", "4096",
                        "-f", "/root/.ssh/id_rsa",
                        "-N", ""
                );
                builder.redirectErrorStream(true); // 合并 stdout 和 stderr
                Process process = builder.start();

                // 读取输出（便于调试）
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("[ssh-keygen] " + line);
                    }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("ssh-keygen failed with exit code " + exitCode);
                }

                log.info("SSH key pair generated successfully.");
            }

            // 读取公钥
            String pub = FileUtil.readUtf8String(publicKey);

            // 创建 Console Connection 并生成 SSH 命令
            CompletableFuture<String> vncStrFuture = CompletableFuture.supplyAsync(() -> {
                OciConsoleUtils build = OciConsoleUtils.builder()
                        .computeClient(fetcher.getComputeClient())
                        .build();
                String connectId = build.createConsoleConnection(params.getInstanceId(), pub);
                return build.waitForConnectionAndGetDetails(connectId, "vnc");
            }, virtualExecutor);

            String vncConnectionString = vncStrFuture.get();

            // 替换 localhost -> 0.0.0.0
            String updated = StrUtil.replace(vncConnectionString, "-L localhost:", "-L 0.0.0.0:");

            // 提取 ProxyCommand 并增强
            String proxyCommand = StrUtil.subBetween(updated, "ProxyCommand='", "'");
            String enhancedProxy = "ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null " + proxyCommand.substring(4);
            updated = StrUtil.replace(updated, proxyCommand, enhancedProxy);

            // 增强主 ssh 命令：禁用交互，不要尝试连接终端
            updated = StrUtil.replaceFirst(updated, "ssh ", "ssh -T -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null ");

            // 加上 nohup 和 & 确保后台运行
            String finalCommand = "nohup " + updated + " > /dev/null 2>&1 &";

            log.info("Starting VNC SSH tunnel for instanceId {}: {}", params.getInstanceId(), finalCommand);

            // 异步后台执行：使用 ProcessBuilder 不等待
            try {
                ProcessBuilder pb = new ProcessBuilder("sh", "-c", finalCommand);
                pb.redirectErrorStream(true);
                pb.start(); // 不等待命令结束
            } catch (Exception e) {
                log.error("Failed to start VNC SSH tunnel", e);
            }

            return resStr;
        } catch (Exception e) {
            log.error("开启 VNC 失败", e);
            throw new OciException(-1, "开启VNC失败", e);
        }
    }

    @Override
    public void autoRescue(AutoRescueParams params) {
        virtualExecutor.execute(() -> {
            SysUserDTO sysUserDTO = getOciUser(params.getOciCfgId());
            try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO);) {
                String instanceId = params.getInstanceId();
                ComputeClient computeClient = fetcher.getComputeClient();
                BlockstorageClient blockstorageClient = fetcher.getBlockstorageClient();
                BootVolume bootVolumeByInstanceId = fetcher.getBootVolumeByInstanceId(instanceId);
                // 检查能否创建AMD实例
                List<AvailabilityDomain> availabilityDomains = fetcher.getAvailabilityDomains(fetcher.getIdentityClient(), fetcher.getCompartmentId());
                List<String> shapeList = availabilityDomains.parallelStream().map(availabilityDomain ->
                                computeClient.listShapes(ListShapesRequest.builder()
                                        .availabilityDomain(availabilityDomain.getName())
                                        .compartmentId(fetcher.getCompartmentId())
                                        .build()).getItems())
                        .flatMap(Collection::stream)
                        .map(Shape::getShape)
                        .distinct()
                        .collect(Collectors.toList());
                ArchitectureEnum type = ArchitectureEnum.getType(ArchitectureEnum.AMD.getType());
                if (shapeList.isEmpty() || !shapeList.contains(type.getShapeDetail())) {
                    log.error("用户：[{}] ，区域：[{}] 开机失败，该区域可能无法创建AMD实例，用户可开机的机型：{}",
                            sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), shapeList);
                    throw new OciException(-1, "当前区域无法创建AMD实例");
                }

                log.warn("用户：[{}]，区域：[{}]，实例：[{}] 开始执行自动救援/缩小硬盘任务...",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), params.getName());

                // 先关机
                log.warn("（1/9）⌛ 正在关机");
                computeClient.instanceAction(InstanceActionRequest.builder()
                        .instanceId(instanceId)
                        .action(InstanceActionEnum.ACTION_STOP.getAction())
                        .build());
                log.info("（1/9）✅ 关机成功");

                while (!fetcher.getInstanceById(instanceId).getLifecycleState().getValue().equals(Instance.LifecycleState.Stopped.getValue())) {
                    Thread.sleep(1000);
                }

                while (!fetcher.getBootVolumeByInstanceId(instanceId).getLifecycleState().getValue().equals(BootVolume.LifecycleState.Available.getValue())) {
                    Thread.sleep(1000);
                }

                // 备份原引导卷
                log.warn("（2/9）⌛ 正在备份原引导卷");
                CreateBootVolumeBackupResponse bootVolumeBackup = blockstorageClient.createBootVolumeBackup(CreateBootVolumeBackupRequest.builder()
                        .createBootVolumeBackupDetails(CreateBootVolumeBackupDetails.builder()
                                .type(CreateBootVolumeBackupDetails.Type.Full)
                                .bootVolumeId(bootVolumeByInstanceId.getId())
                                .displayName("Old-BootVolume-Backup")
                                .build())
                        .build());
                BootVolumeBackup oldBootVolumeBackup = bootVolumeBackup.getBootVolumeBackup();
                log.info("（2/9）✅ 备份原引导卷成功");

                Thread.sleep(3000);

                // 分离原引导卷
                log.warn("（3/9）⌛ 正在分离原引导卷");
                computeClient.detachBootVolume(DetachBootVolumeRequest.builder()
                        .bootVolumeAttachmentId(instanceId)
                        .build());
                log.info("（3/9）✅ 分离原引导卷成功");

                while (!blockstorageClient.getBootVolumeBackup(GetBootVolumeBackupRequest.builder()
                                .bootVolumeBackupId(oldBootVolumeBackup.getId())
                                .build()).getBootVolumeBackup().getLifecycleState().getValue()
                        .equals(BootVolumeBackup.LifecycleState.Available.getValue())) {
                    Thread.sleep(1000);
                }

                // 删除原引导卷
                log.warn("（4/9）⌛ 正在删除原引导卷");
                blockstorageClient.deleteBootVolume(DeleteBootVolumeRequest.builder()
                        .bootVolumeId(bootVolumeByInstanceId.getId())
                        .build());
                log.info("（4/9）✅ 删除原引导卷成功");

                while (!blockstorageClient.getBootVolume(GetBootVolumeRequest.builder()
                        .bootVolumeId(bootVolumeByInstanceId.getId())
                        .build()).getBootVolume().getLifecycleState().getValue().equals(BootVolume.LifecycleState.Terminated.getValue())) {
                    Thread.sleep(1000);
                }

                // 创建47GB的AMD机器
                log.warn("（5/9）⌛ 正在创建并初始化AMD机器，大概需要5分钟，请耐心等待");
                String newAmdSshPwd = "ocihelper2024";
                SysUserDTO newAmd = SysUserDTO.builder()
                        .ociCfg(SysUserDTO.OciCfg.builder()
                                .userId(sysUserDTO.getOciCfg().getUserId())
                                .tenantId(sysUserDTO.getOciCfg().getTenantId())
                                .region(sysUserDTO.getOciCfg().getRegion())
                                .fingerprint(sysUserDTO.getOciCfg().getFingerprint())
                                .privateKeyPath(sysUserDTO.getOciCfg().getPrivateKeyPath())
                                .build())
                        .username(sysUserDTO.getUsername())
                        .ocpus(1.0F)
                        .memory(1.0F)
                        .architecture(ArchitectureEnum.AMD.getType())
                        .createNumbers(1)
                        .operationSystem("Ubuntu")
                        .rootPassword(newAmdSshPwd)
                        .build();
                fetcher.setUser(newAmd);
                InstanceDetailDTO instanceData = fetcher.createInstanceData();
                if (!instanceData.isSuccess()) {
                    log.error("用户：[{}] ，区域：[{}] 创建AMD实例失败", sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion());
                    throw new OciException(-1, "创建AMD实例失败");
                }
                Instance newAmdInstance = instanceData.getInstance();
                // 等待新实例初始化完成
                Thread.sleep(3 * 60 * 1000);
                log.info("（5/9）✅ AMD机器创建并初始化成功");

                // 克隆新建实例引导卷
                log.warn("（6/9）⌛ 正在克隆新建实例引导卷");
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
                log.info("（6/9）✅ 新建实例引导卷克隆成功");

                while (!blockstorageClient.getBootVolume(GetBootVolumeRequest.builder()
                                .bootVolumeId(newAmdInstanceCloneBootVolume.getId())
                                .build()).getBootVolume().getLifecycleState().getValue()
                        .equals(BootVolume.LifecycleState.Available.getValue())) {
                    Thread.sleep(1000);
                }

                // 将新建实例的克隆引导卷附加到需要救砖的实例
                log.warn("（7/9）⌛ 正在将新建实例的克隆引导卷附加到需要救砖的实例");
                AttachBootVolumeResponse attachedBootVolume = computeClient.attachBootVolume(AttachBootVolumeRequest.builder()
                        .attachBootVolumeDetails(AttachBootVolumeDetails.builder()
                                .displayName("New-Boot-Volume")
                                .bootVolumeId(newAmdInstanceCloneBootVolume.getId())
                                .instanceId(instanceId)
                                .build())
                        .build());
                log.info("（7/9）✅ 新建实例的克隆引导卷附加到需要救砖的实例成功");
                log.info(JSONUtil.toJsonStr(attachedBootVolume.getBootVolumeAttachment()));

                while (!fetcher.getBootVolumeById(attachedBootVolume.getBootVolumeAttachment().getBootVolumeId())
                        .getLifecycleState().getValue()
                        .equals(BootVolume.LifecycleState.Available.getValue())) {
                    Thread.sleep(1000);
                }

                log.warn("（8/9）⌛ 正在删除新建的实例、引导卷");
                fetcher.terminateInstance(newAmdInstance.getId(), false, false);
                log.info("（8/9）✅ 删除新建的实例、引导卷成功");

                if (!params.getKeepBackupVolume()) {
                    log.warn("（8/9）⌛ 正在删除原引导卷的备份卷");
                    blockstorageClient.deleteBootVolumeBackup(DeleteBootVolumeBackupRequest.builder()
                            .bootVolumeBackupId(oldBootVolumeBackup.getId())
                            .build());
                    log.info("（8/9）✅ 删除原引导卷的备份卷成功");
                }

                Thread.sleep(3000);

                log.warn("（9/9）⌛ 实例救援成功，正在启动实例...");
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
                Vnic vnic = fetcher.getVnicByInstanceId(instanceId);
                String publicIp = vnic.getPublicIp();
                log.info("（9/9）🎉 实例启动成功 🎉，公网IP：{}，SSH端口：22，SSH账号：root，SSH密码：{}", publicIp, newAmdSshPwd);
                sysService.sendMessage(String.format("【自动救援/缩小硬盘任务】\n\n恭喜！实例自动救援/缩小硬盘成功🎉\n" +
                                "用户：\t%s\n区域：\t%s\n实例：\t%s\n公网IP：\t%s\nSSH端口：\t22\nSSH账号：\troot\nSSH密码：\t%s\n",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), params.getName(),
                        publicIp, newAmdSshPwd));
            } catch (Exception e) {
                log.error("自动救援/缩小硬盘失败", e);
                throw new OciException(-1, "自动救援/缩小硬盘失败，具体原因请查看日志");
            }
        });
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
        ScheduledFuture<?> future = CREATE_INSTANCE_POOL.scheduleWithFixedDelay(() -> VIRTUAL_EXECUTOR.execute(task), initialDelay, period, timeUnit);
        TASK_MAP.put(taskId, future);
    }

    public static void addAtFixedRateTask(String taskId, Runnable task, long initialDelay, long period, TimeUnit timeUnit) {
        ScheduledFuture<?> future = CREATE_INSTANCE_POOL.scheduleAtFixedRate(() -> VIRTUAL_EXECUTOR.execute(task), initialDelay, period, timeUnit);
        TASK_MAP.put(taskId, future);
    }

    public static void stopTask(String taskId) {
        ScheduledFuture<?> future = TASK_MAP.get(taskId);
        if (null != future) {
            future.cancel(false);
        }
        TASK_MAP.remove(taskId);
    }

    public static void execCreate(
            SysUserDTO sysUserDTO, ISysService sysService,
            IInstanceService instanceService,
            IOciCreateTaskService createTaskService) {

        String taskId = CommonUtils.CREATE_TASK_PREFIX + sysUserDTO.getTaskId();
        // 检查是否已经有同一个任务在运行
        if (!RUNNING_TASKS.add(taskId)) {
//            log.warn("【开机任务】任务 [{}] 已在运行中，跳过本轮执行", taskId);
            return;
        }

        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {

            List<InstanceDetailDTO> createInstanceList = instanceService.createInstance(fetcher).getCreateInstanceList();
            long noShapeCounts = createInstanceList.stream().filter(InstanceDetailDTO::isNoShape).count();
            long noPubVcnCounts = createInstanceList.stream().filter(InstanceDetailDTO::isNoPubVcn).count();
            long successCounts = createInstanceList.stream().filter(InstanceDetailDTO::isSuccess).count();
            long outCounts = createInstanceList.stream().filter(InstanceDetailDTO::isOut).count();
            long dieCounts = createInstanceList.stream().filter(InstanceDetailDTO::isDie).count();
            long leftCreateNum = sysUserDTO.getCreateNumbers() - successCounts;

            if (dieCounts > 0) {
                stopAndRemoveTask(sysUserDTO, createTaskService);
                log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] 开机失败，账号可能已无权或已封禁\uD83D\uDC7B，请自行登录官方控制台检查。",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                        sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers());
                sysService.sendMessage(String.format("【开机任务】用户：[%s] ，区域：[%s] ，系统架构：[%s] ，开机数量：[%s] 开机失败，账号可能已无权或已封禁\uD83D\uDC7B，请自行登录官方控制台检查。",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                        sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers()));
            }

            if (noPubVcnCounts > 0) {
                stopAndRemoveTask(sysUserDTO, createTaskService);
                log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] 因无有效公网 VCN 而终止任务...",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                        sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers());
                sysService.sendMessage(String.format("【开机任务】用户：[%s] ，区域：[%s] ，系统架构：[%s] ，开机数量：[%s] 无有效公网 VCN，且无法再创建 VCN，请删除无效的私网 VCN",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                        sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers()));
            }

            if (noShapeCounts > 0) {
                stopAndRemoveTask(sysUserDTO, createTaskService);
                log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] 因不支持 CPU 架构：[{}] 或配额不足而终止任务...",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                        sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers(), sysUserDTO.getArchitecture());
                sysService.sendMessage(String.format("【开机任务】用户：[%s] ，区域：[%s] ，系统架构：[%s] ，开机数量：[%s] 因不支持 CPU 架构：[%s] 或配额不足而终止任务",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                        sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers(), sysUserDTO.getArchitecture()));
            }

            if (sysUserDTO.getCreateNumbers() == outCounts) {
                stopAndRemoveTask(sysUserDTO, createTaskService);
                log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] 因超额而终止任务...",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                        sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers());
                sysService.sendMessage(String.format("【开机任务】用户：[%s] ，区域：[%s] ，系统架构：[%s] ，开机数量：[%s] 因超额而终止任务",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                        sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers()));
            }

            if (sysUserDTO.getCreateNumbers() == successCounts || leftCreateNum == 0) {
                stopAndRemoveTask(sysUserDTO, createTaskService);
                log.warn("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] 任务结束...",
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
            if (e instanceof BmcException) {
                BmcException error = (BmcException) e;
                if (error.getStatusCode() == 401 || error.getMessage().contains(ErrorEnum.NOT_AUTHENTICATED.getErrorType())) {
                    stopAndRemoveTask(sysUserDTO, createTaskService);
                    log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] 开机失败，账号可能已无权或已封禁\uD83D\uDC7B，请自行登录官方控制台检查。",
                            sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                            sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers());
                    sysService.sendMessage(String.format("【开机任务】用户：[%s] ，区域：[%s] ，系统架构：[%s] ，开机数量：[%s] 开机失败，账号可能已无权或已封禁\uD83D\uDC7B，请自行登录官方控制台检查。",
                            sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                            sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers()));
                }
            } else {
                log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] 发生了异常：{}",
                        sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
                        sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers(), e.getLocalizedMessage());
//            stopAndRemoveTask(sysUserDTO, createTaskService);
//            sysService.sendMessage(String.format("【开机任务】用户：[%s] ，区域：[%s] ，系统架构：[%s] ，开机数量：[%s] " +
//                            "发生了异常但并未停止枪机任务，可能是网络响应超时等原因，具体情况自行查看日志",
//                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(),
//                    sysUserDTO.getArchitecture(), sysUserDTO.getCreateNumbers()));
            }
        } finally {
            // 确保任务执行完毕后清除运行标志
            RUNNING_TASKS.remove(taskId);
        }
    }

    private static void stopAndRemoveTask(SysUserDTO sysUserDTO, IOciCreateTaskService createTaskService) {
        TEMP_MAP.remove(CommonUtils.CREATE_COUNTS_PREFIX + sysUserDTO.getTaskId());
        stopTask(CommonUtils.CREATE_TASK_PREFIX + sysUserDTO.getTaskId());
        createTaskService.remove(new LambdaQueryWrapper<OciCreateTask>().eq(OciCreateTask::getId, sysUserDTO.getTaskId()));
        RUNNING_TASKS.remove(CommonUtils.CREATE_TASK_PREFIX + sysUserDTO.getTaskId());
    }

    public void execChange(ChangeIpParams params,
                           SysUserDTO sysUserDTO,
                           IInstanceService instanceService,
                           int randomIntInterval) {
        List<String> cidrList = params.getCidrList();
        String vnicId = params.getVnicId();
        String instanceId = params.getInstanceId();
        if (CollectionUtil.isEmpty(cidrList)) {
            Tuple2<String, Instance> tuple2 = instanceService.changeInstancePublicIp(instanceId, vnicId, sysUserDTO, cidrList);
            if (tuple2.getFirst() == null || tuple2.getSecond() == null) {
                return;
            }
            virtualExecutor.execute(() -> updateCfDns(params, tuple2.getFirst()));
            sendChangeIpMsg(
                    params.getOciCfgId(),
                    sysUserDTO.getUsername(),
                    sysUserDTO.getOciCfg().getRegion(),
                    tuple2.getSecond().getDisplayName(),
                    tuple2.getFirst()
            );
            stopTask(CommonUtils.CHANGE_IP_TASK_PREFIX + instanceId);
            TEMP_MAP.remove(CommonUtils.CHANGE_IP_ERROR_COUNTS_PREFIX + instanceId);
            return;
        }

        Tuple2<String, Instance> tuple2 = instanceService.changeInstancePublicIp(instanceId, vnicId, sysUserDTO, cidrList);
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
            virtualExecutor.execute(() -> updateCfDns(params, publicIp));
            sendChangeIpMsg(params.getOciCfgId(), sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), instanceName, publicIp);
            stopTask(CommonUtils.CHANGE_IP_TASK_PREFIX + instanceId);
            TEMP_MAP.remove(CommonUtils.CHANGE_IP_ERROR_COUNTS_PREFIX + instanceId);
        }
    }

    private void sendChangeIpMsg(String ociCfgId, String username, String region, String instanceName, String publicIp) {
        customCache.remove(CacheConstant.PREFIX_INSTANCE_PAGE + ociCfgId);

        log.info("✔✔✔【更换公共IP】用户：[{}] ，区域：[{}] ，实例：[{}] ，更换公共IP成功，新的公共IP地址：{} ✔✔✔",
                username, region, instanceName,
                publicIp);
        String message = String.format(CommonUtils.CHANGE_IP_MESSAGE_TEMPLATE,
                username,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)),
                region, instanceName, publicIp);
        sysService.sendMessage(message);
    }

    private void updateCfDns(ChangeIpParams params, String publicIp) {
        if (params.isChangeCfDns()) {
            log.info("更换IP成功，开始更新 Cloudflare DNS 记录...");
            CfCfg cfCfg = cfCfgService.getById(params.getSelectedDomainCfgId());
            RemoveCfDnsRecordsParams removeCfDnsRecordsParams = new RemoveCfDnsRecordsParams();
            removeCfDnsRecordsParams.setProxyDomainList(Collections.singletonList(params.getDomainPrefix() + "." + cfCfg.getDomain()));
            removeCfDnsRecordsParams.setZoneId(cfCfg.getZoneId());
            removeCfDnsRecordsParams.setApiToken(cfCfg.getApiToken());
            cfApiService.removeCfDnsRecords(removeCfDnsRecordsParams);

            OciAddCfDnsRecordsParams addCfDnsRecordsParams = new OciAddCfDnsRecordsParams();
            addCfDnsRecordsParams.setCfCfgId(cfCfg.getId());
            addCfDnsRecordsParams.setPrefix(params.getDomainPrefix());
            addCfDnsRecordsParams.setType("A");
            addCfDnsRecordsParams.setIpAddress(publicIp);
            addCfDnsRecordsParams.setProxied(params.isEnableProxy());
            addCfDnsRecordsParams.setTtl(params.getTtl());
            addCfDnsRecordsParams.setComment(params.getRemark());
            cfCfgService.addCfDnsRecord(addCfDnsRecordsParams);
            log.info("Cloudflare DNS 记录更新成功");
        }
    }
}
