package com.yohann.ocihelper.mapper;

import com.yohann.ocihelper.bean.entity.IpData;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yohann.ocihelper.bean.response.ipdata.IpDataPageRsp;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author Yohann_Fan
* @description 针对表【ip_data】的数据库操作Mapper
* @createDate 2025-08-04 17:28:41
* @Entity com.yohann.ocihelper.bean.entity.IpData
*/
public interface IpDataMapper extends BaseMapper<IpData> {

    List<IpDataPageRsp> pageIpData(@Param("offset") long offset,
                                   @Param("size") long size,
                                   @Param("keyword") String keyword);

    Long pageIpDataTotal(@Param("keyword") String keyword);
}




