package com.yohann.ocihelper.bean.response.oci.limits;

import lombok.Data;

/**
 * Single limit/quota item response.
 *
 * @author Yohann
 */
@Data
public class LimitItemRsp {

    /** Service name, e.g. "compute" */
    private String serviceName;

    /** Human-readable limit name */
    private String limitName;

    /** Description of the limit */
    private String description;

    /** Scope type: GLOBAL, REGION, or AD */
    private String scopeType;

    /** Availability domain (only present when scopeType == AD) */
    private String availabilityDomain;

    /** The hard service limit value */
    private Long serviceLimit;

    /** Currently used amount */
    private Long used;

    /** Available (remaining) amount */
    private Long available;
}
