package com.yohann.ocihelper.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yohann.ocihelper.bean.constant.CacheConstant;
import com.yohann.ocihelper.bean.entity.CfCfg;
import com.yohann.ocihelper.bean.params.IdListParams;
import com.yohann.ocihelper.bean.params.cf.*;
import com.yohann.ocihelper.bean.response.cf.GetCfCfgSelRsp;
import com.yohann.ocihelper.bean.response.cf.ListCfCfgPageRsp;
import com.yohann.ocihelper.bean.response.cf.ListCfDnsRecordRsp;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.ICfCfgService;
import com.yohann.ocihelper.mapper.CfCfgMapper;
import com.yohann.ocihelper.service.ICfApiService;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Yohann_Fan
 * @description 针对表【cf_cfg】的数据库操作Service实现
 * @createDate 2025-03-19 16:10:18
 */
@Service
@Slf4j
public class CfCfgServiceImpl extends ServiceImpl<CfCfgMapper, CfCfg> implements ICfCfgService {

    @Resource
    private ICfApiService cfApiService;
    @Resource
    private CustomExpiryGuavaCache<String, Object> customCache;

    @Resource
    private CfCfgMapper cfCfgMapper;

    @Override
    public Page<ListCfCfgPageRsp> listCfg(ListCfCfgParams params) {
        long offset = (long) (params.getCurrentPage() - 1) * params.getPageSize();
        List<ListCfCfgPageRsp> list = cfCfgMapper.listCfg(offset, params.getPageSize(), params.getKeyword());
        Long total = cfCfgMapper.listCfgTotal(params.getKeyword());
        return CommonUtils.buildPage(list, params.getPageSize(), params.getCurrentPage(), total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addCfCfg(AddCfCfgParams params) {
        Optional.ofNullable(this.getOne(new LambdaQueryWrapper<CfCfg>().eq(CfCfg::getDomain, params.getDomain())))
                .ifPresent(x -> {
                    throw new OciException(-1, "域名：" + params.getDomain() + " 已存在");
                });

        CfCfg cfCfg = new CfCfg();
        cfCfg.setId(IdUtil.getSnowflakeNextIdStr());
        cfCfg.setDomain(params.getDomain());
        cfCfg.setZoneId(params.getZoneId());
        cfCfg.setApiToken(params.getApiToken());
        this.save(cfCfg);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCfCfg(IdListParams params) {
        this.removeBatchByIds(params.getIdList());
    }

    @Override
    public void updateCfCfg(UpdateCfCfgParams params) {
        CfCfg cfCfg = Optional.ofNullable(this.getById(params.getId())).orElseThrow(() -> new OciException(-1, "当前配置不存在"));
        cfCfg.setDomain(params.getDomain());
        cfCfg.setZoneId(params.getZoneId());
        cfCfg.setApiToken(params.getApiToken());
        this.updateById(cfCfg);
    }

    @Override
    public void addCfDnsRecord(OciAddCfDnsRecordsParams params) {
        CfCfg cfCfg = Optional.ofNullable(this.getById(params.getCfCfgId())).orElseThrow(() -> new OciException(-1, "当前配置不存在"));
        AddCfDnsRecordsParams addCfDnsRecordsParams = new AddCfDnsRecordsParams();
        addCfDnsRecordsParams.setDomainPrefix(params.getPrefix());
        addCfDnsRecordsParams.setType(params.getType());
        addCfDnsRecordsParams.setProxied(params.isProxied());
        addCfDnsRecordsParams.setIpAddress(params.getIpAddress());
        addCfDnsRecordsParams.setZoneId(cfCfg.getZoneId());
        addCfDnsRecordsParams.setApiToken(cfCfg.getApiToken());
        addCfDnsRecordsParams.setComment(params.getComment());
        addCfDnsRecordsParams.setTtl(params.getTtl());
        handleHttpRsp(cfApiService.addCfDnsRecords(addCfDnsRecordsParams));
    }

    @Override
    public void removeCfDnsRecord(OciRemoveCfDnsRecordsParams params) {
        CfCfg cfCfg = Optional.ofNullable(this.getById(params.getCfCfgId())).orElseThrow(() -> new OciException(-1, "当前配置不存在"));
        RemoveCfDnsByIdsParams removeCfDnsRecordsParams = new RemoveCfDnsByIdsParams();
        removeCfDnsRecordsParams.setRecordIds(params.getRecordIds());
        removeCfDnsRecordsParams.setZoneId(cfCfg.getZoneId());
        removeCfDnsRecordsParams.setApiToken(cfCfg.getApiToken());
        cfApiService.removeCfDnsByIdsRecords(removeCfDnsRecordsParams);
    }

    @Override
    public void updateCfDnsRecord(OciUpdateCfDnsRecordsParams params) {
        CfCfg cfCfg = Optional.ofNullable(this.getById(params.getCfCfgId())).orElseThrow(() -> new OciException(-1, "当前配置不存在"));
//        UpdateCfDnsRecordsParams updateCfDnsRecordsParams = new UpdateCfDnsRecordsParams();
//        updateCfDnsRecordsParams.setZoneId(cfCfg.getZoneId());
//        updateCfDnsRecordsParams.setApiToken(cfCfg.getApiToken());
//        updateCfDnsRecordsParams.setId(params.getId());
//        updateCfDnsRecordsParams.setName(params.getPrefix() + "." + cfCfg.getDomain());
//        updateCfDnsRecordsParams.setType(params.getType());
//        updateCfDnsRecordsParams.setIpAddress(params.getIpAddress());
//        updateCfDnsRecordsParams.setProxied(params.isProxied());
//        updateCfDnsRecordsParams.setTtl(params.getTtl());
//        updateCfDnsRecordsParams.setComment(params.getComment());
//        cfApiService.updateCfDnsRecords(updateCfDnsRecordsParams);

        RemoveCfDnsByIdsParams removeCfDnsRecordsParams = new RemoveCfDnsByIdsParams();
        removeCfDnsRecordsParams.setRecordIds(Collections.singletonList(params.getId()));
        removeCfDnsRecordsParams.setZoneId(cfCfg.getZoneId());
        removeCfDnsRecordsParams.setApiToken(cfCfg.getApiToken());
        cfApiService.removeCfDnsByIdsRecords(removeCfDnsRecordsParams);

        AddCfDnsRecordsParams addCfDnsRecordsParams = new AddCfDnsRecordsParams();
        addCfDnsRecordsParams.setDomainPrefix(params.getPrefix());
        addCfDnsRecordsParams.setType(params.getType());
        addCfDnsRecordsParams.setProxied(params.isProxied());
        addCfDnsRecordsParams.setIpAddress(params.getIpAddress());
        addCfDnsRecordsParams.setZoneId(cfCfg.getZoneId());
        addCfDnsRecordsParams.setApiToken(cfCfg.getApiToken());
        addCfDnsRecordsParams.setComment(params.getComment());
        addCfDnsRecordsParams.setTtl(params.getTtl());
        handleHttpRsp(cfApiService.addCfDnsRecords(addCfDnsRecordsParams));
    }

    @Override
    public Page<ListCfDnsRecordRsp> listCfDnsRecord(ListCfDnsRecordsParams params) {
        if (params.getCleanReLaunch()) {
            customCache.remove(CacheConstant.PREFIX_VCN_PAGE + params.getCfCfgId());
        }

        CfCfg cfCfg = Optional.ofNullable(this.getById(params.getCfCfgId())).orElseThrow(() -> new OciException(-1, "当前配置不存在"));
        List<ListCfDnsRecordRsp> cfDnsRecordRspList = (List<ListCfDnsRecordRsp>) customCache.get(CacheConstant.PREFIX_CF_DNS_RECORDS + params.getCfCfgId());
        if (CollectionUtil.isEmpty(cfDnsRecordRspList)) {
            GetCfDnsRecordsParams getCfDnsRecordsParams = new GetCfDnsRecordsParams();
            getCfDnsRecordsParams.setZoneId(cfCfg.getZoneId());
            getCfDnsRecordsParams.setApiToken(cfCfg.getApiToken());
            cfDnsRecordRspList = Optional.ofNullable(cfApiService.getCfDnsRecords(getCfDnsRecordsParams))
                    .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
                    .map(x -> {
                        ListCfDnsRecordRsp rsp = new ListCfDnsRecordRsp();
                        BeanUtils.copyProperties(x, rsp);
                        rsp.setCreatedOn(x.getCreatedOn().format(CommonUtils.DATETIME_FMT_NORM));
                        rsp.setModifiedOn(x.getModifiedOn().format(CommonUtils.DATETIME_FMT_NORM));
                        return rsp;
                    })
//                    .sorted(Comparator.comparing(ListCfDnsRecordRsp::getName))
                    .collect(Collectors.toList());
        }

        customCache.put(CacheConstant.PREFIX_VCN_PAGE + params.getCfCfgId(), cfDnsRecordRspList, 10 * 60 * 1000);
        List<ListCfDnsRecordRsp> dnsRecordsRsp = cfDnsRecordRspList.parallelStream()
                .filter(x -> CommonUtils.contains(x.getName(), params.getKeyword(), true) ||
                        CommonUtils.contains(x.getContent(), params.getKeyword(), true) ||
                        CommonUtils.contains(x.getComment(), params.getKeyword(), true))
                .collect(Collectors.toList());
        List<ListCfDnsRecordRsp> page = CommonUtils.getPage(dnsRecordsRsp, params.getCurrentPage(), params.getPageSize());
        return CommonUtils.buildPage(page, params.getPageSize(), params.getCurrentPage(), dnsRecordsRsp.size());
    }

    @Override
    public GetCfCfgSelRsp getCfCfgSel() {
        return new GetCfCfgSelRsp(Optional.ofNullable(this.list())
                .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).stream()
                .map(x -> new GetCfCfgSelRsp.CfCfgSel(x.getId(), x.getDomain()))
                .collect(Collectors.toList()));
    }

    private void handleHttpRsp(HttpResponse httpResponse) {
        if (!Boolean.parseBoolean(String.valueOf(JSONUtil.parseObj(httpResponse.body()).get("success")))) {
            log.error("请求失败，接口返回数据：{}", httpResponse.body());
            throw new OciException(-1, "请求失败，接口返回数据：" + httpResponse.body());
        }
    }
}




