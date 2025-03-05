package com.yohann.ocihelper.mapper;

import com.yohann.ocihelper.bean.entity.OciUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yohann.ocihelper.bean.response.oci.cfg.OciUserListRsp;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author Administrator
 * @description 针对表【oci_user】的数据库操作Mapper
 * @createDate 2024-11-12 16:44:39
 * @Entity com.yohann.ocihelper.bean.entity.OciUser
 */
public interface OciUserMapper extends BaseMapper<OciUser> {

    List<OciUserListRsp> userPage(@Param("offset") long offset,
                                  @Param("size") long size,
                                  @Param("keyword") String keyword,
                                  @Param("enableTask") Integer enableTask);

    Long userPageTotal(@Param("keyword") String keyword,
                       @Param("enableTask") Integer enableTask);
}




