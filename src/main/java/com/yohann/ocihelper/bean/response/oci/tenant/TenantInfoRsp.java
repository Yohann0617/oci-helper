package com.yohann.ocihelper.bean.response.oci.tenant;

import com.oracle.bmc.identity.model.User;
import lombok.Data;

import java.util.List;

/**
 * @ClassName TenantInfoRsp
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-10 14:58
 **/
@Data
public class TenantInfoRsp {

    private String id;
    private String name;
    private String description;
    private String homeRegionKey;
    private String upiIdcsCompatibilityLayerEndpoint;
    private List<User> userList;
}
