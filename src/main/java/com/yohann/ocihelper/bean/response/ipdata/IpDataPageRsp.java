package com.yohann.ocihelper.bean.response.ipdata;

import lombok.Data;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.response.ipdata
 * @className: IpDataPageRsp
 * @author: Yohann
 * @date: 2025/8/5 23:12
 */
@Data
public class IpDataPageRsp {

    private String id;
    private String ip;
    private String country;
    private String area;
    private String city;
    private String org;
    private String asn;
    private Double lat;
    private Double lng;
    private String createTime;
}
