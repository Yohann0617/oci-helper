package com.yohann.ocihelper.bean.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * @TableName cf_cfg
 */
@TableName(value ="cf_cfg")
@Data
public class CfCfg implements Serializable {
    private String id;

    private String domain;

    private String zoneId;

    private String apiToken;

    private Date createTime;

    private static final long serialVersionUID = 1L;
}