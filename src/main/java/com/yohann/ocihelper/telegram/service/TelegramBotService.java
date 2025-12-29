package com.yohann.ocihelper.telegram.service;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciCreateTask;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.bean.response.oci.traffic.FetchInstancesRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.service.IOciCreateTaskService;
import com.yohann.ocihelper.service.IOciUserService;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.service.ITrafficService;
import com.yohann.ocihelper.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.yohann.ocihelper.config.VirtualThreadConfig.VIRTUAL_EXECUTOR;
import static com.yohann.ocihelper.service.impl.OciServiceImpl.TEMP_MAP;

/**
 * Telegram Bot 业务逻辑服务
 * 
 * @author yohann
 */
@Slf4j
@Service
public class TelegramBotService {
    
    /**
     * 检查所有 OCI 配置的 API 测活
     * 
     * @return 结果消息
     */
    public String checkAlive() {
        ISysService sysService = SpringUtil.getBean(ISysService.class);
        IOciUserService userService = SpringUtil.getBean(IOciUserService.class);
        
        List<String> ids = userService.listObjs(new LambdaQueryWrapper<OciUser>()
                .isNotNull(OciUser::getId)
                .select(OciUser::getId), String::valueOf);
        
        if (CollectionUtil.isEmpty(ids)) {
            return "暂无配置";
        }
        
        List<String> failNames = ids.parallelStream().filter(id -> {
            SysUserDTO ociUser = sysService.getOciUser(id);
            try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(ociUser)) {
                fetcher.getAvailabilityDomains();
            } catch (Exception e) {
                return true;
            }
            return false;
        }).map(id -> sysService.getOciUser(id).getUsername()).collect(Collectors.toList());
        
        return String.format(
                "【API测活结果】\n\n" +
                "✅ 有效配置数：%s\n" +
                "❌ 失效配置数：%s\n" +
                "\uD83D\uDD11 总配置数：%s\n" +
                "⚠\uFE0F 失效配置：\n%s",
                ids.size() - failNames.size(),
                failNames.size(),
                ids.size(),
                CollectionUtil.isEmpty(failNames) ? "无" : String.join("\n", failNames)
        );
    }
    
    /**
     * 获取任务详情
     * 
     * @return 任务详情消息
     */
    public String getTaskDetails() {
        IOciUserService userService = SpringUtil.getBean(IOciUserService.class);
        IOciCreateTaskService createTaskService = SpringUtil.getBean(IOciCreateTaskService.class);
        
        String message = "【任务详情】\n\n" +
                "\uD83D\uDD58 时间：\t%s\n" +
                "\uD83D\uDECE 正在执行的开机任务：\n%s\n";
        
        CompletableFuture<String> task = CompletableFuture.supplyAsync(() -> {
            List<OciCreateTask> ociCreateTaskList = createTaskService.list();
            if (ociCreateTaskList.isEmpty()) {
                return "无";
            }
            
            String template = "[%s] [%s] [%s] [%s核/%sGB/%sGB] [%s台] [%s] [%s次]";
            return ociCreateTaskList.parallelStream().map(x -> {
                OciUser ociUser = userService.getById(x.getUserId());
                Long counts = (Long) TEMP_MAP.get(CommonUtils.CREATE_COUNTS_PREFIX + x.getId());
                return String.format(
                        template,
                        ociUser.getUsername(),
                        ociUser.getOciRegion(),
                        x.getArchitecture(),
                        x.getOcpus().longValue(),
                        x.getMemory().longValue(),
                        x.getDisk(),
                        x.getCreateNumbers(),
                        CommonUtils.getTimeDifference(x.getCreateTime()),
                        counts == null ? "0" : counts
                );
            }).collect(Collectors.joining("\n"));
        }, VIRTUAL_EXECUTOR);
        
        CompletableFuture.allOf(task).join();
        
        return String.format(
                message,
                LocalDateTime.now().format(CommonUtils.DATETIME_FMT_NORM),
                task.join()
        );
    }
    
    /**
     * 获取流量统计
     * 
     * @return 流量统计消息
     */
    public String getTrafficStatistics() {
        IOciUserService userService = SpringUtil.getBean(IOciUserService.class);
        ITrafficService trafficService = SpringUtil.getBean(ITrafficService.class);
        
        List<OciUser> ociUserList = userService.list();
        if (CollectionUtil.isEmpty(ociUserList)) {
            return "暂无配置信息";
        }
        
        return "【流量统计】\n\n" + Optional.ofNullable(userService.list())
                .filter(CollectionUtil::isNotEmpty)
                .orElseGet(Collections::emptyList)
                .parallelStream()
                .map(ociCfg -> {
                    FetchInstancesRsp fetchInstancesRsp;
                    try {
                        fetchInstancesRsp = trafficService.fetchInstances(ociCfg.getId(), ociCfg.getOciRegion());
                    } catch (Exception e) {
                        return "";
                    }
                    return String.format(
                            "\uD83D\uDD58 时间：%s\n" +
                            "🔑 配置名：【%s】\n" +
                            "🌏 主区域：【%s】\n" +
                            "\uD83D\uDDA5 实例数量：【%s】 台\n" +
                            "⬇ 本月入站流量总计：%s\n" +
                            "⬆ 本月出站流量总计：%s\n",
                            LocalDateTime.now().format(CommonUtils.DATETIME_FMT_NORM),
                            ociCfg.getUsername(),
                            ociCfg.getOciRegion(),
                            fetchInstancesRsp.getInstanceCount(),
                            fetchInstancesRsp.getInboundTraffic(),
                            fetchInstancesRsp.getOutboundTraffic()
                    );
                })
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("\n"));
    }
}
