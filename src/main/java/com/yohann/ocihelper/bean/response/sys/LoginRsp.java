package com.yohann.ocihelper.bean.response.sys;

import lombok.Data;

/**
 * @ClassName LoginRsp
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-07-08 16:08
 **/
@Data
public class LoginRsp {

    private String token;
    private String currentVersion;
    private String latestVersion;
}
