package com.yohann.ocihelper.bean.response.oci.cfg;

import lombok.Data;

/**
 * <p>
 * OciUserListRsp
 * </p >
 *
 * @author yohann
 * @since 2024/11/12 17:25
 */
@Data
public class OciUserListRsp {

    private String id;
    private String username;
    private String tenantName;
    private String region;
    private String createTime;
    private Integer enableCreate;
}
