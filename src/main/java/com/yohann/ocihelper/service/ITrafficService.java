package com.yohann.ocihelper.service;

import com.yohann.ocihelper.bean.dto.ValueLabelDTO;
import com.yohann.ocihelper.bean.params.oci.traffic.GetTrafficDataParams;
import com.yohann.ocihelper.bean.response.oci.traffic.FetchInstancesRsp;
import com.yohann.ocihelper.bean.response.oci.traffic.GetConditionRsp;
import com.yohann.ocihelper.bean.response.oci.traffic.GetTrafficDataRsp;

import java.util.List;

public interface ITrafficService {
    GetTrafficDataRsp getData(GetTrafficDataParams params);

    GetConditionRsp getCondition(String ociCfgId);

    FetchInstancesRsp fetchInstances(String ociCfgId, String region);

    List<ValueLabelDTO> fetchVnics(String ociCfgId, String region, String instanceId);

}
