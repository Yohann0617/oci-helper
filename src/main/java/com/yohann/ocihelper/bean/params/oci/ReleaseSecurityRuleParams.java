package com.yohann.ocihelper.bean.params.oci;

import lombok.Data;

import javax.validation.constraints.NotBlank;

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
