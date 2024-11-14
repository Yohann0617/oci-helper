package com.yohann.ocihelper.bean.params;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * <p>
 * LoginParams
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/14 12:04
 */
@Data
public class LoginParams {

    @NotBlank(message = "账号不能为空")
    private String account;
    @NotBlank(message = "密码不能为空")
    private String password;
}
