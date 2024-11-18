package com.yohann.ocihelper.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oracle.bmc.core.model.Vnic;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciCreateTask;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.bean.params.*;
import com.yohann.ocihelper.bean.response.CreateTaskRsp;
import com.yohann.ocihelper.bean.response.OciCfgDetailsRsp;
import com.yohann.ocihelper.bean.response.OciUserListRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.enums.OciCfgEnum;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.mapper.OciCreateTaskMapper;
import com.yohann.ocihelper.service.IInstanceService;
import com.yohann.ocihelper.service.IOciCreateTaskService;
import com.yohann.ocihelper.service.IOciService;
import com.yohann.ocihelper.service.IOciUserService;
import com.yohann.ocihelper.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import com.yohann.ocihelper.mapper.OciUserMapper;

import java.io.File;
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
            Runtime.getRuntime().availableProcessors() * 2,
            ThreadFactoryBuilder.create().setNamePrefix("oci-create-").build());
    public final static Map<String, Object> TEMP_MAP = new ConcurrentHashMap<>();

    public static void execCreate(SysUserDTO sysUserDTO, IInstanceService instanceService) {
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            instanceService.createInstance(fetcher);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        CREATE_INSTANCE_POOL.scheduleWithFixedDelay(() -> execCreate(sysUserDTO, instanceService),
                0, params.getInterval(), TimeUnit.SECONDS);
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

        TEMP_MAP.put(params.getInstanceId(), params);
        OciUser ociUser = userService.getById(params.getOciCfgId());
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
        CompletableFuture.runAsync(() -> {
            instanceService.changeInstancePublicIp(params.getInstanceId(), sysUserDTO, params.getCidrList());
        });
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
