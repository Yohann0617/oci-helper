package com.yohann.ocihelper.bean.params.oci.tenant;

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotBlank;

/**
 * @ClassName UpdateUserInfoParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-14 18:07
 **/
@EqualsAndHashCode(callSuper = true)
@Data
public class UpdateUserInfoParams extends UpdateUserBasicParams{

    @NotBlank(message = "邮箱不能为空")
    private String email;
    @NotBlank(message = "dbUserName不能为空")
    private String dbUserName;
    private String description;
}
