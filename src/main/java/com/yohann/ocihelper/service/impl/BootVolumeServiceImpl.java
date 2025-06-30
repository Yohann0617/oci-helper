package com.yohann.ocihelper.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oracle.bmc.core.model.BootVolume;
import com.oracle.bmc.core.model.Instance;
import com.yohann.ocihelper.bean.Tuple2;
import com.yohann.ocihelper.bean.constant.CacheConstant;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.oci.volume.BootVolumePageParams;
import com.yohann.ocihelper.bean.params.oci.volume.TerminateBootVolumeParams;
import com.yohann.ocihelper.bean.params.oci.volume.UpdateBootVolumeParams;
import com.yohann.ocihelper.bean.response.oci.volume.BootVolumeListPage;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.IBootVolumeService;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * <p>
 * BootVolumeServiceImpl
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/12/31 15:50
 */
@Service
@Slf4j
public class BootVolumeServiceImpl implements IBootVolumeService {

    @Resource
    private ISysService sysService;
    @Resource
    private CustomExpiryGuavaCache<String, Object> customCache;

    @Override
    public Page<BootVolumeListPage.BootVolumeInfo> bootVolumeListPage(BootVolumePageParams params) {
        if (params.isCleanReLaunch()) {
            customCache.remove(CacheConstant.PREFIX_BOOT_VOLUME_PAGE + params.getOciCfgId());
        }
        List<BootVolumeListPage.BootVolumeInfo> bootVolumeInCache =
                (List<BootVolumeListPage.BootVolumeInfo>) customCache.get(CacheConstant.PREFIX_BOOT_VOLUME_PAGE + params.getOciCfgId());
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        if (ObjUtil.isEmpty(bootVolumeInCache)) {
            try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
                bootVolumeInCache = fetcher.listBootVolume().parallelStream().map(x -> {
                    Tuple2<Boolean, String> attachInstance = getAttachInstance(sysUserDTO, x.getId());
                    BootVolumeListPage.BootVolumeInfo bootVolumeInfo = new BootVolumeListPage.BootVolumeInfo();
                    bootVolumeInfo.setId(x.getId());
                    bootVolumeInfo.setAvailabilityDomain(x.getAvailabilityDomain());
                    bootVolumeInfo.setDisplayName(x.getDisplayName());
                    bootVolumeInfo.setVpusPerGB(x.getVpusPerGB() + "");
                    bootVolumeInfo.setSizeInGBs(x.getSizeInGBs() + "");
                    bootVolumeInfo.setLifecycleState(x.getLifecycleState().getValue());
                    bootVolumeInfo.setTimeCreated(DateUtil.format(x.getTimeCreated(), CommonUtils.DATETIME_FMT_NORM));
                    bootVolumeInfo.setAttached(attachInstance.getFirst());
                    bootVolumeInfo.setInstanceName(attachInstance.getSecond());
                    bootVolumeInfo.setJsonStr(JSONUtil.toJsonStr(x));
                    return bootVolumeInfo;
                }).collect(Collectors.toList());
            } catch (Exception e) {
                log.error("获取引导卷列表失败", e);
                throw new OciException(-1, "获取引导卷列表失败");
            }
        }
        customCache.put(CacheConstant.PREFIX_BOOT_VOLUME_PAGE + params.getOciCfgId(), bootVolumeInCache, 10 * 60 * 1000);

        List<BootVolumeListPage.BootVolumeInfo> resList = bootVolumeInCache.parallelStream()
                .filter(x -> CommonUtils.contains(x.getDisplayName(), params.getKeyword(), true) ||
                        CommonUtils.contains(x.getAvailabilityDomain(), params.getKeyword(), true) ||
                        CommonUtils.contains(x.getLifecycleState(), params.getKeyword(), true) ||
                        CommonUtils.contains(x.getTimeCreated(), params.getKeyword(), true) ||
                        CommonUtils.contains(x.getInstanceName(), params.getKeyword(), true))
                .collect(Collectors.toList()).parallelStream()
                .sorted(Comparator.comparing(BootVolumeListPage.BootVolumeInfo::getDisplayName)).collect(Collectors.toList());

        List<BootVolumeListPage.BootVolumeInfo> pageList = CommonUtils.getPage(resList, params.getCurrentPage(), params.getPageSize());
        return BootVolumeListPage.buildPage(pageList, params.getPageSize(), params.getCurrentPage(), pageList.size());
    }

    @Override
    public void terminateBootVolume(TerminateBootVolumeParams params) {
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        CompletableFuture.runAsync(() -> {
            params.getBootVolumeIds().parallelStream().forEach(id -> {
                String bvName = null;
                try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
                    fetcher.terminateBootVolume(id);
                    bvName = fetcher.getBootVolumeById(id).getDisplayName();
                    log.info("用户：[{}] ， 区域：[{}] ，正在终止引导卷：[{}]",
                            sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), bvName);
                } catch (Exception e) {
                    log.error("引导卷：{} 终止异常", bvName, e);
                }
            });
        });
    }

    @Override
    public void update(UpdateBootVolumeParams params) {
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            fetcher.updateBootVolumeCfg(params.getBootVolumeId(),
                    Long.parseLong(params.getBootVolumeSize()),
                    Long.parseLong(params.getBootVolumeVpu()));
        } catch (Exception e) {
            log.error("更改引导卷配置失败", e);
            throw new OciException(-1, "更改引导卷配置失败");
        }
    }

    private Tuple2<Boolean, String> getAttachInstance(SysUserDTO sysUserDTO, String bootVolumeId) {
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            List<Instance> instances = fetcher.listInstances();
            if (instances.isEmpty()) {
                return Tuple2.of(false, null);
            }
            for (Instance instance : instances) {
                try {
                    List<BootVolume> bootVolumes = fetcher.listBootVolumeListByInstanceId(instance.getId());
                    if (!bootVolumes.isEmpty()) {
                        if (bootVolumes.parallelStream().map(BootVolume::getId).collect(Collectors.toList()).contains(bootVolumeId)) {
                            return Tuple2.of(true, instance.getDisplayName());
                        }
                    }
                } catch (Exception e) {
                    log.error("获取实例引导卷列表失败", e);
                }
            }
        } catch (Exception e) {
            log.error("获取实例引导卷列表失败", e);
        }
        return Tuple2.of(false, null);
    }
}
