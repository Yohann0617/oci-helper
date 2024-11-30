package com.yohann.ocihelper.bean.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 
 * @TableName OciKv
 */
@TableName(value ="oci_kv")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OciKv implements Serializable {

    @TableId
    private String id;

    private String code;

    private String value;

    private String type;

    private LocalDateTime createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}