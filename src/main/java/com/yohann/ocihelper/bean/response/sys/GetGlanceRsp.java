package com.yohann.ocihelper.bean.response.sys;

import lombok.Data;

/**
 * <p>
 * GetGlanceRsp
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/12/25 14:11
 */
@Data
public class GetGlanceRsp {

    private String users;
    private String tasks;
    private String regions;
    private String days;
    private String currentVersion;
}
