package com.yohann.ocihelper.bean.params.oci.cfg;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params
 * @className: SendCaptchaParams
 * @author: Yohann
 * @date: 2024/11/28 21:55
 */
@Data
public class SendCaptchaParams {

    @NotBlank(message = "配置id不能为空")
    private String ociCfgId;

    @NotBlank(message = "实例id不能为空")
    private String instanceId;
}
