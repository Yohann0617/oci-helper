package com.yohann.ocihelper.bean.response;

import lombok.Data;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.response
 * @className: CreateTaskRsp
 * @author: Yohann
 * @date: 2024/11/15 21:42
 */
@Data
public class CreateTaskRsp {

    private String id;

    private String username;

    private String ocpus;

    private String memory;

    private Integer disk;

    private String architecture;

    private Integer interval;

    private Integer createNumbers;

    private String operationSystem;

    private String createTime;
}
