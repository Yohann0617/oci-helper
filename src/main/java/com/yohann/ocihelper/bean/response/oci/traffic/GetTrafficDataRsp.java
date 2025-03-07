package com.yohann.ocihelper.bean.response.oci.traffic;

import lombok.Data;

import java.util.List;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.response.oci.traffic
 * @className: GetTrafficDataRsp
 * @author: Yohann
 * @date: 2025/3/7 21:26
 */
@Data
public class GetTrafficDataRsp {

    List<String> time;
    List<String> inbound;
    List<String> outbound;
}
