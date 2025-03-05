package com.yohann.ocihelper.bean.params.oci.instance;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params
 * @className: UpdateInstanceStateParams
 * @author: Yohann
 * @date: 2024/11/28 21:28
 */
@Data
public class UpdateInstanceStateParams {

    @NotBlank(message = "配置id不能为空")
    private String ociCfgId;

    @NotBlank(message = "实例id不能为空")
    private String instanceId;

    @NotBlank(message = "实例操作不能为空")
    private String action;

}
