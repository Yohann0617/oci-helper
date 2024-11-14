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
 * @TableName oci_user
 */
@TableName(value ="oci_user")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OciUser implements Serializable {

    @TableId
    private String id;

    private String username;

    private String ociTenantId;

    private String ociUserId;

    private String ociFingerprint;

    private String ociRegion;

    private String ociKeyPath;

    private LocalDateTime createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}