package com.yohann.ocihelper.bean.params.cf;

import lombok.Data;

import java.util.List;

/**
 * @ClassName RemoveCfDnsByIdParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-21 17:49
 **/
@Data
public class RemoveCfDnsByIdsParams {

    private List<String> recordIds;
    private String zoneId;
    private String apiToken;
}
