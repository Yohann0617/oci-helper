package com.yohann.ocihelper.bean.params.oci.securityrule;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


/**
 * <p>
 * ReleaseSecurityRuleParams
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/12/18 17:59
 */
@Data
public class ReleaseSecurityRuleParams {

    @NotBlank(message = "配置id不能为空")
    private String ociCfgId;
}
