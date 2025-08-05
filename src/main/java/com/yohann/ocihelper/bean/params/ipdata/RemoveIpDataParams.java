package com.yohann.ocihelper.bean.params.ipdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
public class RemoveIpDataParams {

    @NotEmpty(message = "id列表不能为空")
    private List<String> idList;
}
