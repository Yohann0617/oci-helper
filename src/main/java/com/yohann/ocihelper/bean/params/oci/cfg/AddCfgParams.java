package com.yohann.ocihelper.bean.params.oci.cfg;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * <p>
 * AddCfgParams
 * </p >
 *
 * @author yohann
 * @since 2024/11/13 14:30
 */
@Data
public class AddCfgParams {

    @NotBlank(message = "配置名称不能为空")
    private String username;

    @NotBlank(message = "配置不能为空")
    private String ociCfgStr;
}
