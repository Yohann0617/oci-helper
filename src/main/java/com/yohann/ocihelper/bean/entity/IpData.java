package com.yohann.ocihelper.bean.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * @TableName ip_data
 */
@TableName(value ="ip_data")
@Data
public class IpData implements Serializable {
    private String id;

    private String ip;

    private String country;

    private String area;

    private String city;

    private String org;

    private String asn;

    private String type;

    private Double lat;

    private Double lng;

    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}