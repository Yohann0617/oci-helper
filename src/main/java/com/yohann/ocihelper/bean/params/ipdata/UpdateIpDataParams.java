package com.yohann.ocihelper.bean.params.ipdata;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params.ipdata
 * @className: AddIpDataParams
 * @author: Yohann
 * @date: 2025/8/5 21:55
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateIpDataParams {

    @NotBlank(message = "id不能为空")
    private String id;
}
