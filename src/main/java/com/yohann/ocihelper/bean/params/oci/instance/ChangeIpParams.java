package com.yohann.ocihelper.bean.params.oci.instance;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params
 * @className: ChangeIpParams
 * @author: Yohann
 * @date: 2024/11/14 0:03
 */
@Data
public class ChangeIpParams {

    @NotBlank(message = "配置id不能为空")
    private String ociCfgId;

    @NotBlank(message = "实例id不能为空")
    private String instanceId;

    private List<String> cidrList;
}
