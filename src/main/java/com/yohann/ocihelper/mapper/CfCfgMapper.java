package com.yohann.ocihelper.mapper;

import com.yohann.ocihelper.bean.entity.CfCfg;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yohann.ocihelper.bean.response.cf.ListCfCfgPageRsp;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author Yohann_Fan
 * @description 针对表【cf_cfg】的数据库操作Mapper
 * @createDate 2025-03-19 16:10:18
 * @Entity com.yohann.ocihelper.bean.entity.CfCfg
 */
public interface CfCfgMapper extends BaseMapper<CfCfg> {

    List<ListCfCfgPageRsp> listCfg(@Param("offset") long offset,
                                   @Param("size") long size,
                                   @Param("keyword") String keyword);

    Long listCfgTotal(@Param("keyword") String keyword);
}




