package com.yohann.ocihelper.bean.params.oci;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

/**
 * <p>
 * UpdateInstanceNameParams
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/12/18 18:11
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UpdateInstanceNameParams extends GetInstanceCfgInfoParams {

    @NotBlank(message = "实例名称不能为空")
    private String name;
}
