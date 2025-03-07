package com.yohann.ocihelper.controller;

import com.yohann.ocihelper.bean.ResponseData;
import com.yohann.ocihelper.bean.dto.ValueLabelDTO;
import com.yohann.ocihelper.bean.params.oci.traffic.GetTrafficDataParams;
import com.yohann.ocihelper.bean.response.oci.traffic.FetchInstancesRsp;
import com.yohann.ocihelper.bean.response.oci.traffic.GetConditionRsp;
import com.yohann.ocihelper.bean.response.oci.traffic.GetTrafficDataRsp;
import com.yohann.ocihelper.service.ITrafficService;
import com.yohann.ocihelper.utils.CommonUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.controller
 * @className: TrafficStatisticsController
 * @author: Yohann
 * @date: 2025/3/7 20:35
 */
@RestController
@RequestMapping(path = "/api/traffic")
public class TrafficStatisticsController {

    @Resource
    private ITrafficService trafficService;

    @RequestMapping("data")
    public ResponseData<GetTrafficDataRsp> getData(@Validated @RequestBody GetTrafficDataParams params,
                                                   BindingResult bindingResult) {
        CommonUtils.checkAndThrow(bindingResult);
        return ResponseData.successData(trafficService.getData(params), "获取流量数据成功");
    }

    @RequestMapping("getCondition")
    public ResponseData<GetConditionRsp> getCondition(@RequestParam("ociCfgId") String ociCfgId) {
        return ResponseData.successData(trafficService.getCondition(ociCfgId), "获取查询条件成功");
    }

    @RequestMapping("fetchInstances")
    public ResponseData<FetchInstancesRsp> fetchInstances(@RequestParam("ociCfgId") String ociCfgId,
                                                          @RequestParam("region") String region) {
        return ResponseData.successData(trafficService.fetchInstances(ociCfgId, region), "获取区域实例成功");
    }

    @RequestMapping("fetchVnics")
    public ResponseData<List<ValueLabelDTO>> fetchVnics(@RequestParam("ociCfgId") String ociCfgId,
                                                        @RequestParam("region") String region,
                                                        @RequestParam("instanceId") String instanceId) {
        return ResponseData.successData(trafficService.fetchVnics(ociCfgId, region, instanceId), "获取区域实例成功");
    }

}
