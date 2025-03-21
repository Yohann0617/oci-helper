package com.yohann.ocihelper.bean.response.cf;

import lombok.Data;

import java.util.List;

/**
 * @ClassName ListCfDnsRecordRsp
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-21 15:03
 **/
@Data
public class ListCfDnsRecordRsp {

    private String id;
    private String name;
    private String type;
    private String content;
    private Boolean proxiable;
    private Boolean proxied;
    private Integer ttl;
    private String comment;
    private String createdOn;
    private String modifiedOn;
    private List<String> tags;
}
