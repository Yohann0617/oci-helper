package com.yohann.ocihelper.bean.params.oci.traffic;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params.oci.traffic
 * @className: GetTrafficDataParams
 * @author: Yohann
 * @date: 2025/3/7 20:37
 */
@Data
public class GetTrafficDataParams {

    @NotBlank(message = "配置ID不能为空")
    private String ociCfgId;
    @NotNull(message = "开始时间不能为空")
    private Date beginTime;
    @NotNull(message = "结束时间不能为空")
    private Date endTime;
    @NotBlank(message = "区域不能为空")
    private String region;
    @NotBlank(message = "inQuery不能为空")
    private String inQuery;
    @NotBlank(message = "outQuery不能为空")
    private String outQuery;
    @NotBlank(message = "namespace不能为空")
    private String namespace;
}
