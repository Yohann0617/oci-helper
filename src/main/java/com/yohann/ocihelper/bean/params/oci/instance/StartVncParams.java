package com.yohann.ocihelper.bean.params.oci.instance;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @ClassName StartVncParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-06-04 11:02
 **/
@Data
public class StartVncParams {

    @NotBlank(message = "配置ID不能为空")
    private String ociCfgId;
    private String compartmentId;
    @NotBlank(message = "实例ID不能为空")
    private String instanceId;
}
