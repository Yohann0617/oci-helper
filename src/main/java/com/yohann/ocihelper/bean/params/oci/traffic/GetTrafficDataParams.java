package com.yohann.ocihelper.bean.params.oci.traffic;

import lombok.Data;

import java.util.Date;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params.oci.traffic
 * @className: GetTrafficDataParams
 * @author: Yohann
 * @date: 2025/3/7 20:37
 */
@Data
public class GetTrafficDataParams {

    private String ociCfgId;
    private Date beginTime;
    private Date endTime;
    private String region;
    private String inQuery;
    private String outQuery;
    private String namespace;
}
