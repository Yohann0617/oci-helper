package com.yohann.ocihelper.bean.params.oci.securityrule;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @ClassName RemoveSecurityRuleParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-05 16:06
 **/
@Data
public class RemoveSecurityRuleParams {

    @NotBlank(message = "api配置id不能为空")
    private String ociCfgId;
    @NotBlank(message = "vcnId不能为空")
    private String vcnId;
    private Integer type;
    private List<String> ruleIds;
}
