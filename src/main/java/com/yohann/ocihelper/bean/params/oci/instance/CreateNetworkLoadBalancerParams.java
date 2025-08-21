package com.yohann.ocihelper.bean.params.oci.instance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @ClassName CreateNetworkLoadBalancerParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-08-21 09:41
 **/
@Data
public class CreateNetworkLoadBalancerParams {

    @NotBlank(message = "ociCfgId不能为空")
    private String ociCfgId;
    @NotBlank(message = "instanceId不能为空")
    private String instanceId;
    @NotNull(message = "sshPort不能为空")
    private Integer sshPort;
}
