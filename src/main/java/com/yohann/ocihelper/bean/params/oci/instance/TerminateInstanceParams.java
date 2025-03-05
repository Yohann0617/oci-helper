package com.yohann.ocihelper.bean.params.oci.instance;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params
 * @className: TerminateInstanceParams
 * @author: Yohann
 * @date: 2024/11/28 21:48
 */
@Data
public class TerminateInstanceParams {

    @NotBlank(message = "配置id不能为空")
    private String ociCfgId;

    @NotBlank(message = "实例id不能为空")
    private String instanceId;

    @NotNull(message = "是否保留引导卷不能为空")
    private Integer preserveBootVolume;

    @NotBlank(message = "验证码不能为空")
    private String captcha;
}
