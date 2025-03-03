package com.yohann.ocihelper.bean.params.oci.vcn;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params.oci.vcn
 * @className: RemoveVcnParams
 * @author: Yohann
 * @date: 2025/3/3 22:59
 */
@Data
public class RemoveVcnParams {

    @NotEmpty(message = "vcnId列表不能为空")
    private List<String> vcnIds;
    @NotBlank(message = "配置id不能为空")
    private String ociCfgId;
}
