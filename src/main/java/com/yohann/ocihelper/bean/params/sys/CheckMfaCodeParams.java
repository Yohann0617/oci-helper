package com.yohann.ocihelper.bean.params.sys;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params.sys
 * @className: CheckMfaCodeParams
 * @author: Yohann
 * @date: 2024/11/30 18:32
 */
@Data
public class CheckMfaCodeParams {

    @NotBlank(message = "mfaCode不能为空")
    private String mfaCode;
}
