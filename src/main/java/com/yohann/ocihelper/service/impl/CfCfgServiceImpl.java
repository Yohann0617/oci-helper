package com.yohann.ocihelper.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yohann.ocihelper.bean.entity.CfCfg;
import com.yohann.ocihelper.bean.params.IdListParams;
import com.yohann.ocihelper.bean.params.cf.AddCfCfgParams;
import com.yohann.ocihelper.bean.params.cf.ListCfCfgParams;
import com.yohann.ocihelper.bean.params.cf.UpdateCfCfgParams;
import com.yohann.ocihelper.bean.response.cf.ListCfCfgPageRsp;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.ICfCfgService;
import com.yohann.ocihelper.mapper.CfCfgMapper;
import com.yohann.ocihelper.service.ICfService;
import com.yohann.ocihelper.utils.CommonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

/**
 * @author Yohann_Fan
 * @description 针对表【cf_cfg】的数据库操作Service实现
 * @createDate 2025-03-19 16:10:18
 */
@Service
public class CfCfgServiceImpl extends ServiceImpl<CfCfgMapper, CfCfg> implements ICfCfgService {

    @Resource
    private ICfService cfService;

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
        CfCfg cfCfg = Optional.ofNullable(this.getById(params.getId()))
                .orElseThrow(() -> new OciException(-1, "当前配置不存在"));
        cfCfg.setDomain(params.getDomain());
        cfCfg.setZoneId(params.getZoneId());
        cfCfg.setApiToken(params.getApiToken());
        this.updateById(cfCfg);
    }
}




