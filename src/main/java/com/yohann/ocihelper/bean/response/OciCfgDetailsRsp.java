package com.yohann.ocihelper.bean.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.response
 * @className: OciCfgDetailsRsp
 * @author: Yohann
 * @date: 2024/11/13 23:54
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OciCfgDetailsRsp {

    private String userId;
    private String tenantId;
    private String fingerprint;
    private String privateKeyPath;
    private String region;
    private List<InstanceInfo> instanceList;

    @Data
    public static class InstanceInfo {
        private String ocId;
        private String region;
        private String name;
        private List<String> publicIp;
        private String shape;
        private Integer enableChangeIp = 0;
    }

}
