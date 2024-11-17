package com.yohann.ocihelper.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.Vnic;
import com.oracle.bmc.model.BmcException;
import com.yohann.ocihelper.bean.dto.InstanceDetailDTO;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciCreateTask;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.enums.MessageTypeEnum;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.IInstanceService;
import com.yohann.ocihelper.service.IOciCreateTaskService;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.MessageServiceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static com.yohann.ocihelper.service.impl.OciServiceImpl.TEMP_MAP;

/**
 * <p>
 * InstanceServiceImpl
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/11 14:30
 */
@Slf4j
@Service
public class InstanceServiceImpl implements IInstanceService {

    @Resource
    private MessageServiceFactory messageServiceFactory;
    @Resource
    private IOciCreateTaskService createTaskService;

    private static final String LEGACY_MESSAGE_TEMPLATE =
            "🎉 用户：%s 开机成功 🎉\n" +
                    "---------------------------\n" +
                    "时间： %s\n" +
                    "Region： %s\n" +
                    "CPU： %s\n" +
                    "内存（GB）： %s\n" +
                    "磁盘大小（GB）： %s\n" +
                    "Shape： %s\n" +
                    "公网IP： %s\n" +
                    "root密码： %s\n" +
                    "---------------------------\n" +
                    "⭐注意： 如果没有开机任务请及时清理API";

    @Override
    public List<SysUserDTO.CloudInstance> listRunningInstances(OracleInstanceFetcher fetcher) {
        return fetcher.listInstances().parallelStream()
                .map(x -> SysUserDTO.CloudInstance.builder()
                        .region(x.getRegion())
                        .name(x.getDisplayName())
                        .ocId(x.getId())
                        .shape(x.getShape())
                        .publicIp(fetcher.listInstanceIPs(x.getId()).stream().map(Vnic::getPublicIp).collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public InstanceDetailDTO createInstance(OracleInstanceFetcher fetcher) {
        if (createTaskService.getById(fetcher.getUser().getTaskId()) == null) {
            log.warn("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] 任务终止......",
                    fetcher.getUser().getUsername(), fetcher.getUser().getOciCfg().getRegion(),
                    fetcher.getUser().getArchitecture(), fetcher.getUser().getCreateNumbers());
            throw new OciException(-1, "任务终止");
        }
        InstanceDetailDTO instanceDetail = fetcher.createInstanceData();
        if (instanceDetail.isOut()) {
            createTaskService.remove(new LambdaQueryWrapper<OciCreateTask>()
                    .eq(OciCreateTask::getId, instanceDetail.getTaskId()));
            log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] ，开机数量：[{}] 因异常而终止任务......",
                    instanceDetail.getUsername(), instanceDetail.getRegion(),
                    instanceDetail.getArchitecture(), instanceDetail.getLeftCreateNumbers());
        }
        if (instanceDetail.isSuccess()) {
            if (instanceDetail.getLeftCreateNumbers() <= 0) {
                createTaskService.remove(new LambdaQueryWrapper<OciCreateTask>()
                        .eq(OciCreateTask::getId, instanceDetail.getTaskId()));
                TEMP_MAP.remove(CommonUtils.CREATE_COUNTS_PREFIX + instanceDetail.getTaskId());
            }
            log.info("---------------- 🎉 用户：{} 开机成功 🎉 ----------------", instanceDetail.getUsername());
            log.info("Region: {}", instanceDetail.getRegion());
            log.info("CPU: {}", instanceDetail.getOcpus());
            log.info("内存（GB）: {}", instanceDetail.getMemory());
            log.info("磁盘大小（GB）: {}", instanceDetail.getDisk());
            log.info("Shape: {}", instanceDetail.getShape());
            log.info("公网IP: {}", instanceDetail.getPublicIp());
            log.info("root密码: {}", instanceDetail.getRootPassword());
            log.info("-------------------------------------------------------");
            String message = String.format(LEGACY_MESSAGE_TEMPLATE,
                    instanceDetail.getUsername(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)),
                    instanceDetail.getRegion(),
                    instanceDetail.getOcpus(),
                    instanceDetail.getMemory(),
                    instanceDetail.getDisk(),
                    instanceDetail.getShape(),
                    instanceDetail.getPublicIp(),
                    instanceDetail.getRootPassword());
            try {
                messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_TELEGRAM).sendMessage(message);
            } catch (Exception e) {
                log.error("【开机任务】用户：[{}] ，区域：[{}] ，系统架构：[{}] 开机成功，实例IP：{} ，但是消息发送失败",
                        instanceDetail.getUsername(), instanceDetail.getRegion(),
                        instanceDetail.getShape(), instanceDetail.getPublicIp());
            }

            createTaskService.update(new LambdaUpdateWrapper<OciCreateTask>()
                    .eq(OciCreateTask::getId, instanceDetail.getTaskId())
                    .set(OciCreateTask::getCreateNumbers, instanceDetail.getLeftCreateNumbers()));
        }
        return instanceDetail;
    }

    @Override
    public String changeInstancePublicIp(OracleInstanceFetcher fetcher, Instance instance, List<String> cidrList) {
        log.info("---------------------------- 用户：[{}] ，区域：[{}] ，实例：[{}] 开始执行更换公共IP任务 -------------------------------",
                fetcher.getUser().getUsername(), fetcher.getUser().getOciCfg().getRegion(), instance.getDisplayName());
        String publicIp = null;
        if (CollectionUtil.isEmpty(cidrList)) {
            return fetcher.reassignEphemeralPublicIp(fetcher.listInstanceIPs(instance.getId()).get(0));
        }

        // 循环尝试获取符合 CIDR 范围的 IP
        do {
            try {
                publicIp = fetcher.reassignEphemeralPublicIp(fetcher.listInstanceIPs(instance.getId()).get(0));
                int randomIntInterval = ThreadLocalRandom.current().nextInt(60 * 1000, 80 * 1000);
                if (!CommonUtils.isIpInCidrList(publicIp, cidrList)) {
                    log.warn("【更换公共IP】用户：[{}] ，区域：[{}] ，实例：[{}] ，获取到的IP：{} 不在给定的 CIDR 网段中，{} 秒后将继续更换公共IP...",
                            fetcher.getUser().getUsername(), fetcher.getUser().getOciCfg().getRegion(), instance.getDisplayName(),
                            publicIp, randomIntInterval / 1000);
                } else {
                    log.info("✔✔✔【更换公共IP】用户：[{}] ，区域：[{}] ，实例：[{}] ，更换公共IP成功，新的公共IP地址：{} ✔✔✔",
                            fetcher.getUser().getUsername(), fetcher.getUser().getOciCfg().getRegion(), instance.getDisplayName(),
                            publicIp);
                    TEMP_MAP.remove(instance.getId());
                    break;
                }
                Thread.sleep(randomIntInterval);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (BmcException ociException) {
                log.error("【更换公共IP】用户：[{}] ，区域：[{}] ，实例：[{}] ，更换公共IP失败，原因：{}",
                        fetcher.getUser().getUsername(), fetcher.getUser().getOciCfg().getRegion(), instance.getDisplayName(),
                        ociException.getMessage());
            }
        } while (!CommonUtils.isIpInCidrList(publicIp, cidrList) && TEMP_MAP.get(instance.getId()) != null);
        return publicIp;
    }

}
