package com.yohann.ocihelper.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oracle.bmc.core.model.BootVolume;
import com.yohann.ocihelper.bean.constant.CacheConstant;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.oci.BootVolumePageParams;
import com.yohann.ocihelper.bean.response.oci.BootVolumeListPage;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.IBootVolumeService;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
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
        List<BootVolume> bootVolumes;
        List<BootVolume> bootVolumeInCache = (List<BootVolume>) customCache.get(CacheConstant.PREFIX_BOOT_VOLUME_PAGE);
        if (ObjUtil.isNotEmpty(bootVolumeInCache)) {
            bootVolumes = bootVolumeInCache;
        } else {
            SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
            try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
                bootVolumes = fetcher.listBootVolume();
            } catch (Exception e) {
                log.error("获取引导卷列表失败", e);
                throw new OciException(-1, "获取引导卷列表失败");
            }
            customCache.put(CacheConstant.PREFIX_BOOT_VOLUME_PAGE + params.getOciCfgId(), bootVolumes, 10 * 60 * 1000);
        }

        List<BootVolumeListPage.BootVolumeInfo> resList = CollectionUtil.isEmpty(bootVolumes) ? Collections.emptyList() :
                bootVolumes.parallelStream().filter(x -> CommonUtils.contains(x.getDisplayName(), params.getKeyword(), true) ||
                        CommonUtils.contains(x.getAvailabilityDomain(), params.getKeyword(), true) ||
                        CommonUtils.contains(x.getLifecycleState().getValue(), params.getKeyword(), true) ||
                        CommonUtils.contains(DateUtil.format(x.getTimeCreated(), CommonUtils.DATETIME_FMT_NORM), params.getKeyword(), true))
                        .collect(Collectors.toList()).parallelStream().map(x -> {
                    BootVolumeListPage.BootVolumeInfo bootVolumeInfo = new BootVolumeListPage.BootVolumeInfo();
                    bootVolumeInfo.setId(x.getId());
                    bootVolumeInfo.setAvailabilityDomain(x.getAvailabilityDomain());
                    bootVolumeInfo.setDisplayName(x.getDisplayName());
                    bootVolumeInfo.setVpusPerGB(x.getVpusPerGB() + "");
                    bootVolumeInfo.setSizeInGBs(x.getSizeInGBs() + "");
                    bootVolumeInfo.setLifecycleState(x.getLifecycleState().getValue());
                    bootVolumeInfo.setTimeCreated(DateUtil.format(x.getTimeCreated(), CommonUtils.DATETIME_FMT_NORM));
                    bootVolumeInfo.setJsonStr(JSONUtil.toJsonStr(x));
                    return bootVolumeInfo;
                }).collect(Collectors.toList());

        List<BootVolumeListPage.BootVolumeInfo> pageList = CommonUtils.getPage(resList, params.getCurrentPage(), params.getPageSize());
        return BootVolumeListPage.buildPage(pageList, params.getPageSize(), params.getCurrentPage(), pageList.size());
    }
}
