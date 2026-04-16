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

    private String tenantName;

    private LocalDateTime tenantCreateTime;

    private String ociTenantId;

    private String ociUserId;

    private String ociFingerprint;

    private String ociRegion;

    private String ociKeyPath;

    private String planType;

    private LocalDateTime createTime;

    /** 单独代理地址，优先级高于全局代理，例如 http://host:port 或 socks5://host:port */
    private String proxy;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}