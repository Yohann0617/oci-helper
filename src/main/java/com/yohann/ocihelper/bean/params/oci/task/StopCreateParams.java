package com.yohann.ocihelper.bean.params.oci.task;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * <p>
 * StopCreateParams
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/14 12:05
 */
@Data
public class StopCreateParams {

    @NotBlank(message = "用户配置id不能为空")
    private String userId;
}
