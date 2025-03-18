package com.yohann.ocihelper.bean.params.oci.tenant;

import lombok.Data;

/**
 * @ClassName GetTenantInfoParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-12 15:57
 **/
@Data
public class GetTenantInfoParams {

    private String ociCfgId;
    private String region;
    private boolean cleanReLaunch;
}
