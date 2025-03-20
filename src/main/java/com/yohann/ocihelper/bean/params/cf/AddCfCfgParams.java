package com.yohann.ocihelper.bean.params.cf;

import lombok.Data;

/**
 * @ClassName AddCfCfgParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-20 16:57
 **/
@Data
public class AddCfCfgParams {

    private String domain;
    private String zoneId;
    private String apiToken;
}
