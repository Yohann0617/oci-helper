package com.yohann.ocihelper.bean.response.cf;

import lombok.Data;

/**
 * @ClassName ListCfCfgPageRsp
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-20 13:47
 **/
@Data
public class ListCfCfgPageRsp {

    private String id;

    private String domain;

    private String zoneId;

    private String apiToken;

    private String createTime;
}
