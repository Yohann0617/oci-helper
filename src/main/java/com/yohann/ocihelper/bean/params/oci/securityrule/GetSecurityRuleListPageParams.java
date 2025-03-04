package com.yohann.ocihelper.bean.params.oci.securityrule;

import com.yohann.ocihelper.bean.params.BasicPageParams;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params.oci.securityrule
 * @className: GetSecurityRuleListPageParams
 * @author: Yohann
 * @date: 2025/3/1 16:08
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class GetSecurityRuleListPageParams extends BasicPageParams {

    @NotBlank(message = "api配置id不能为空")
    private String ociCfgId;
    @NotBlank(message = "vcnId不能为空")
    private String vcnId;
    private Integer type;
    private boolean cleanReLaunch;
}
