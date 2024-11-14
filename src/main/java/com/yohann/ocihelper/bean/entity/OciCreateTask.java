package com.yohann.ocihelper.bean.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 
 * @TableName oci_create_task
 */
@TableName(value ="oci_create_task")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OciCreateTask implements Serializable {

    @TableId
    private String id;

    private String userId;

    private Float ocpus;

    private Float memory;

    private Integer disk;

    private String architecture;

    private Integer interval;

    private Integer createNumbers;

    private String rootPassword;

    private String operationSystem;

    private LocalDateTime createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}