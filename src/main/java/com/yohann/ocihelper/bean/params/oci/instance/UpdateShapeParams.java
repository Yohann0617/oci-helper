package com.yohann.ocihelper.bean.params.oci.instance;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @ClassName UpdateShapeParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-09-15 09:54
 **/
@Data
public class UpdateShapeParams {

    @NotBlank(message = "ociCfgId不能为空")
    private String ociCfgId;
    @NotBlank(message = "instanceId不能为空")
    private String instanceId;
    @NotBlank(message = "shape不能为空，例：VM.Standard.A1.Flex")
    private String shape;
}
