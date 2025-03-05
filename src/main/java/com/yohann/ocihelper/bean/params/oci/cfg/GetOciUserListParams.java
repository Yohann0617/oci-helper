package com.yohann.ocihelper.bean.params.oci.cfg;

import lombok.Data;

/**
 * <p>
 * GetOciUserListParams
 * </p >
 *
 * @author yohann
 * @since 2024/11/12 17:24
 */
@Data
public class GetOciUserListParams {

    private String keyword;
    private long currentPage;
    private long pageSize;
    private Integer isEnableCreate;
}
