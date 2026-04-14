package com.yohann.ocihelper.bean.response.oci.limits;

import lombok.Data;

import java.util.List;

/**
 * Response containing paginated limit/usage items.
 *
 * @author Yohann
 */
@Data
public class GetLimitsRsp {

    /** Total count of items (across all pages returned in this response) */
    private int total;

    /** Limit and usage records */
    private List<LimitItemRsp> items;
}
