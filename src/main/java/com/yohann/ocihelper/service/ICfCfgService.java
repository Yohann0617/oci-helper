package com.yohann.ocihelper.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yohann.ocihelper.bean.entity.CfCfg;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yohann.ocihelper.bean.params.IdListParams;
import com.yohann.ocihelper.bean.params.cf.AddCfCfgParams;
import com.yohann.ocihelper.bean.params.cf.ListCfCfgParams;
import com.yohann.ocihelper.bean.params.cf.UpdateCfCfgParams;
import com.yohann.ocihelper.bean.response.cf.ListCfCfgPageRsp;

/**
* @author Yohann_Fan
* @description 针对表【cf_cfg】的数据库操作Service
* @createDate 2025-03-19 16:10:18
*/
public interface ICfCfgService extends IService<CfCfg> {

    Page<ListCfCfgPageRsp> listCfg(ListCfCfgParams params);

    void addCfCfg(AddCfCfgParams params);

    void removeCfCfg(IdListParams params);

    void updateCfCfg(UpdateCfCfgParams params);
}
