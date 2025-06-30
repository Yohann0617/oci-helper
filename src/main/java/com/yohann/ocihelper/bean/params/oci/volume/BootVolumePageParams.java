package com.yohann.ocihelper.bean.params.oci.volume;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * <p>
 * BootVolumePageParams
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/12/31 15:32
 */
@Data
public class BootVolumePageParams {

    @NotBlank(message = "配置id不能为空")
    private String ociCfgId;
    private String keyword;
    private int currentPage;
    private int pageSize;
    private boolean cleanReLaunch;
}
