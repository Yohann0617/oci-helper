package com.yohann.ocihelper.controller;

import com.yohann.ocihelper.bean.ResponseData;
import com.yohann.ocihelper.bean.params.oci.limits.GetLimitsParams;
import com.yohann.ocihelper.bean.response.oci.limits.GetLimitsRsp;
import com.yohann.ocihelper.service.ILimitsService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for OCI service limits / quota queries.
 *
 * @author Yohann
 */
@RestController
@RequestMapping("/api/limits")
public class LimitsController {

    @Resource
    private ILimitsService limitsService;

    /**
     * Query service limits and quotas for a specific OCI configuration and region.
     *
     * @param params request body containing ociCfgId, region, and optional serviceName
     * @return list of limit items with usage information
     */
    @PostMapping("/query")
    public ResponseData<GetLimitsRsp> query(@Validated @RequestBody GetLimitsParams params) {
        return ResponseData.successData(limitsService.getLimits(params), "查询配额成功");
    }

    /**
     * List available service names for the given OCI configuration and region.
     * Used to populate the service-name filter drop-down on the front end.
     *
     * @param ociCfgId OCI configuration ID
     * @param region   region identifier, e.g. "ap-seoul-1"
     * @return sorted list of service names
     */
    @GetMapping("/services")
    public ResponseData<List<String>> services(@RequestParam("ociCfgId") String ociCfgId,
                                               @RequestParam("region") String region) {
        return ResponseData.successData(limitsService.getServiceNames(ociCfgId, region), "获取服务列表成功");
    }
}
