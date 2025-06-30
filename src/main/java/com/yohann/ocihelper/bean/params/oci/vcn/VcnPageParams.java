package com.yohann.ocihelper.bean.params.oci.vcn;

import com.yohann.ocihelper.bean.params.BasicPageParams;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotBlank;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params.oci.vcn
 * @className: VcnPageParams
 * @author: Yohann
 * @date: 2025/3/3 21:02
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class VcnPageParams extends BasicPageParams {

    @NotBlank(message = "配置id不能为空")
    private String ociCfgId;
    private boolean cleanReLaunch;
}
