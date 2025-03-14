package com.yohann.ocihelper.bean.params.oci.tenant;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @ClassName UpdateUserInfoParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-14 18:07
 **/
@EqualsAndHashCode(callSuper = true)
@Data
public class UpdateUserInfoParams extends UpdateUserBasicParams{

    private String email;
    private String dbUserName;
    private String description;
}
