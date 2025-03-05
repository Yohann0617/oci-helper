package com.yohann.ocihelper.bean.params.oci.instance;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

/**
 * <p>
 * UpdateInstanceCfgParams
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/12/18 18:13
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UpdateInstanceCfgParams extends GetInstanceCfgInfoParams {

    @NotBlank(message = "cpu不能为空")
    private String ocpus;
    @NotBlank(message = "内存不能为空")
    private String memory;
}
