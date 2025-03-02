package com.yohann.ocihelper.bean.params.oci.securityrule;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params.oci.securityrule
 * @className: GetSecurityRuleListParams
 * @author: Yohann
 * @date: 2025/3/1 16:08
 */
@Data
public class GetSecurityRuleListParams {

    @NotBlank(message = "api配置id不能为空")
    private String ociCfgId;
    @NotBlank(message = "vcnId不能为空")
    private String vcnId;

}
