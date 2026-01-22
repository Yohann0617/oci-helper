package com.yohann.ocihelper.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.IpData;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.bean.params.ipdata.AddIpDataParams;
import com.yohann.ocihelper.bean.params.ipdata.PageIpDataParams;
import com.yohann.ocihelper.bean.params.ipdata.RemoveIpDataParams;
import com.yohann.ocihelper.bean.params.ipdata.UpdateIpDataParams;
import com.yohann.ocihelper.bean.response.ipdata.IpDataPageRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.enums.IpDataTypeEnum;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.IInstanceService;
import com.yohann.ocihelper.service.IIpDataService;
import com.yohann.ocihelper.mapper.IpDataMapper;
import com.yohann.ocihelper.service.IOciUserService;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.utils.CommonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * @author Yohann_Fan
 * @description 针对表【ip_data】的数据库操作Service实现
 * @createDate 2025-08-04 17:28:41
 */
@Service
@Slf4j
public class IpDataServiceImpl extends ServiceImpl<IpDataMapper, IpData> implements IIpDataService {

    @Resource
    private IInstanceService instanceService;
    @Resource
    private ISysService sysService;
    @Resource
    private IOciUserService ociUserService;
    @Resource
    private ExecutorService virtualExecutor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(AddIpDataParams params) {
        try {
            String jsonStr = HttpUtil.get(String.format("http://ip-api.com/json/%s?fields=status,message,country,regionName,city,lat,lon,org,as,query", params.getIp()));
            JSONObject json = JSONUtil.parseObj(jsonStr);

            // Check API response status
            if (!"success".equals(json.getStr("status"))) {
                throw new OciException(-1, "IP查询失败: " + json.getStr("message"));
            }

            IpData ipData = new IpData();
            ipData.setId(IdUtil.getSnowflakeNextIdStr());
            ipData.setIp(json.getStr("query"));
            ipData.setCountry(json.getStr("country"));
            ipData.setArea(json.getStr("regionName"));
            ipData.setCity(json.getStr("city"));
            ipData.setOrg(json.getStr("org"));
            ipData.setAsn(json.getStr("as"));
            ipData.setLat(json.getDouble("lat"));
            ipData.setLng(json.getDouble("lon"));

            List<IpData> ipDataList = this.list(new LambdaQueryWrapper<IpData>()
                    .eq(IpData::getIp, json.getStr("query")));
            if (CollectionUtil.isNotEmpty(ipDataList)) {
                this.remove(new LambdaQueryWrapper<IpData>().eq(IpData::getIp, json.getStr("query")));
            }
            this.save(ipData);
        } catch (Exception e) {
            log.error("Failed to add IP data for: {}", params.getIp(), e);
            throw new OciException(-1, "IP数据添加失败: " + e.getMessage());
        }
    }

    @Override
    public void loadOciIpData() {
        virtualExecutor.execute(() -> {
            log.info("【同步IP数据任务】开始同步已有的oci配置的最新IP数据...");
            this.remove(new LambdaQueryWrapper<IpData>().eq(IpData::getType, IpDataTypeEnum.IP_DATA_ORACLE.getCode()));
            log.info("【同步IP数据任务】清除已有的oci配置的旧IP数据成功");
            List<String> ociCfgIds = ociUserService.listObjs(new LambdaQueryWrapper<OciUser>().select(OciUser::getId), String::valueOf);
            if (CollectionUtil.isNotEmpty(ociCfgIds)) {
                for (String x : ociCfgIds) {
                    SysUserDTO ociUser = sysService.getOciUser(x);
                    try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(ociUser)) {
                        fetcher.getAvailabilityDomains();
                    } catch (Exception e) {
                        log.warn("oci配置：[{}]，区域：[{}] 已失效，跳过本次IP数据同步", ociUser.getUsername(), ociUser.getOciCfg().getRegion());
                        continue;
                    }
                    List<SysUserDTO.CloudInstance> cloudInstances = instanceService.listRunningInstances(ociUser);
                    if (CollectionUtil.isEmpty(cloudInstances)) {
                        continue;
                    }
                    for (SysUserDTO.CloudInstance y : cloudInstances) {
                        if (CollectionUtil.isEmpty(y.getPublicIp())) {
                            continue;
                        }
                        for (String z : y.getPublicIp()) {
                            try {
                                String jsonStr = HttpUtil.get(String.format("http://ip-api.com/json/%s?fields=status,message,country,regionName,city,lat,lon,org,as,query", z));
                                JSONObject json = JSONUtil.parseObj(jsonStr);

                                // Check API response status
                                if (!"success".equals(json.getStr("status"))) {
                                    log.warn("IP查询失败: IP={}, message={}", z, json.getStr("message"));
                                    continue;
                                }

                                IpData ipData = new IpData();
                                ipData.setId(IdUtil.getSnowflakeNextIdStr());
                                ipData.setIp(json.getStr("query"));
                                ipData.setCountry(json.getStr("country"));
                                ipData.setArea(json.getStr("regionName"));
                                ipData.setCity(json.getStr("city"));
                                ipData.setOrg(json.getStr("org"));
                                ipData.setAsn(json.getStr("as"));
                                ipData.setLat(json.getDouble("lat"));
                                ipData.setLng(json.getDouble("lon"));
                                ipData.setType(IpDataTypeEnum.IP_DATA_ORACLE.getCode());
                                if (this.save(ipData)) {
                                    log.info("oci配置：[{}]，区域：[{}]，IP地址：[{}] 已添加至地图IP数据", ociUser.getUsername(), ociUser.getOciCfg().getRegion(), z);
                                }
                            } catch (Exception e) {
                                log.error("oci配置：[{}]，区域：[{}]，IP地址：[{}] 添加至地图IP数据失败", ociUser.getUsername(), ociUser.getOciCfg().getRegion(), z, e);
                            }
                        }
                    }
                }
            }
            log.info("【同步IP数据任务】任务完成");
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateIpData(UpdateIpDataParams params) {
        IpData ipData = Optional.ofNullable(this.getById(params.getId())).orElseThrow(() -> new OciException(-1, "当前记录不存在"));
        try {
            String jsonStr = HttpUtil.get(String.format("http://ip-api.com/json/%s?fields=status,message,country,regionName,city,lat,lon,org,as,query", ipData.getIp()));
            JSONObject json = JSONUtil.parseObj(jsonStr);

            // Check API response status
            if (!"success".equals(json.getStr("status"))) {
                throw new OciException(-1, "IP查询失败: " + json.getStr("message"));
            }

            this.update(new LambdaUpdateWrapper<IpData>().eq(IpData::getId, params.getId())
                    .set(IpData::getIp, json.getStr("query"))
                    .set(IpData::getCountry, json.getStr("country"))
                    .set(IpData::getArea, json.getStr("regionName"))
                    .set(IpData::getCity, json.getStr("city"))
                    .set(IpData::getOrg, json.getStr("org"))
                    .set(IpData::getAsn, json.getStr("as"))
                    .set(IpData::getLat, json.getDouble("lat"))
                    .set(IpData::getLng, json.getDouble("lon")));
        } catch (Exception e) {
            log.error("Failed to update IP data for: {}", ipData.getIp(), e);
            throw new OciException(-1, "IP数据更新失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeIpData(RemoveIpDataParams params) {
        this.removeByIds(params.getIdList());
    }

    @Override
    public Page<IpDataPageRsp> pageIpData(PageIpDataParams params) {
        List<IpDataPageRsp> list = this.baseMapper.pageIpData(params.getOffset(), params.getPageSize(), params.getKeyword());
        Long total = this.baseMapper.pageIpDataTotal(params.getKeyword());
        return CommonUtils.buildPage(list, params.getPageSize(), params.getCurrentPage(), total);
    }
}




