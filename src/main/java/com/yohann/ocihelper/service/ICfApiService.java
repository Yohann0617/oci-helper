package com.yohann.ocihelper.service;

import cn.hutool.http.HttpResponse;
import com.yohann.ocihelper.bean.dto.CfDnsRecordDTO;
import com.yohann.ocihelper.bean.params.cf.*;

import java.util.List;

/**
 * @ClassName ICfService
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-19 14:32
 **/
public interface ICfApiService {

    HttpResponse addCfDnsRecords(AddCfDnsRecordsParams params);

    void removeCfDnsRecords(RemoveCfDnsRecordsParams params);

    void removeCfDnsByIdsRecords(RemoveCfDnsByIdsParams params);

    HttpResponse updateCfDnsRecords(UpdateCfDnsRecordsParams params);

    List<CfDnsRecordDTO> getCfDnsRecords(GetCfDnsRecordsParams params);
}
