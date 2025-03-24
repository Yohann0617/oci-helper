package com.yohann.ocihelper.bean.params.cf;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @ClassName AddCfCfgParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-20 16:57
 **/
@Data
public class AddCfCfgParams {

    @NotBlank(message = "域名不能为空")
    private String domain;
    @NotBlank(message = "区域ID不能为空")
    private String zoneId;
    @NotBlank(message = "API令牌不能为空")
    private String apiToken;
}
