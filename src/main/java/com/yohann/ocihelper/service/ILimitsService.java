package com.yohann.ocihelper.service;

import com.yohann.ocihelper.bean.params.oci.limits.GetLimitsParams;
import com.yohann.ocihelper.bean.response.oci.limits.GetLimitsRsp;

import java.util.List;

/**
 * Service interface for querying OCI service limits and quotas.
 *
 * @author Yohann
 */
public interface ILimitsService {

    /**
     * Query limit definitions along with their current usage and availability.
     *
     * @param params query parameters (ociCfgId, region, optional serviceName)
     * @return response containing limit items
     */
    GetLimitsRsp getLimits(GetLimitsParams params);

    /**
     * List all available service names for a given OCI configuration and region.
     * Used to populate the service-name filter drop-down.
     *
     * @param ociCfgId OCI configuration ID
     * @param region   region identifier, e.g. "ap-seoul-1"
     * @return sorted list of service names, e.g. ["blockstorage", "compute", "vcn", ...]
     */
    List<String> getServiceNames(String ociCfgId, String region);
}
