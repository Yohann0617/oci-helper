package com.yohann.ocihelper.bean.params.oci;

import com.yohann.ocihelper.bean.params.IdParams;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params.oci
 * @className: GetOciCfgDetailsParams
 * @author: Yohann
 * @date: 2025/1/3 23:20
 */
@Data
public class GetOciCfgDetailsParams {
    @NotBlank(message = "id不能为空")
    private String cfgId;
    private boolean cleanReLaunchDetails;
}
