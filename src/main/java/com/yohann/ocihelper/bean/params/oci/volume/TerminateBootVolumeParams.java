package com.yohann.ocihelper.bean.params.oci.volume;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * <p>
 * TerminateBootVolumeParams
 * </p >
 *
 * @author yuhui.fan
 * @since 2025/1/3 19:04
 */
@Data
public class TerminateBootVolumeParams {

    @NotBlank(message = "配置id不能为空")
    private String ociCfgId;
    @NotEmpty(message = "引导卷id不能为空")
    private List<String> bootVolumeIds;
}
