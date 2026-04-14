package com.yohann.ocihelper.bean.params.oci.limits;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询 OCI 服务限额与使用量的请求参数
 *
 * @author Yohann
 */
@Data
public class GetLimitsParams {

    @NotBlank(message = "配置ID不能为空")
    private String ociCfgId;

    /** 指定区域，如 ap-seoul-1；必填 */
    @NotBlank(message = "区域不能为空")
    private String region;

    /** 按服务名称筛选，如 compute；留空则查询全部 */
    private String serviceName;
}
