package com.yohann.ocihelper.bean.params.oci.tenant;

import lombok.Data;

/**
 * @ClassName UpdateUserBasicParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-14 18:04
 **/
@Data
public class UpdateUserBasicParams {

    private String ociCfgId;
    private String userId;

}
