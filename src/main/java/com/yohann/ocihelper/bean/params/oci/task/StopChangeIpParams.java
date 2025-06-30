package com.yohann.ocihelper.bean.params.oci.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * <p>
 * StopChangeIpParams
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/14 12:06
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StopChangeIpParams {

    @NotBlank(message = "实例id不能为空")
    private String instanceId;
}
