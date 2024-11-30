package com.yohann.ocihelper.mapper;

import com.yohann.ocihelper.bean.entity.OciCreateTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yohann.ocihelper.bean.response.oci.CreateTaskRsp;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author Administrator
* @description 针对表【oci_create_task】的数据库操作Mapper
* @createDate 2024-11-12 16:44:38
* @Entity com.yohann.ocihelper.bean.entity.OciCreateTask
*/
public interface OciCreateTaskMapper extends BaseMapper<OciCreateTask> {

    List<CreateTaskRsp> createTaskPage(@Param("offset") long offset,
                                       @Param("size") long size,
                                       @Param("keyword") String keyword,
                                       @Param("architecture") String architecture);

    Long createTaskPageTotal(@Param("keyword") String keyword,
                             @Param("architecture") String architecture);

}




