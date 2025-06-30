package com.yohann.ocihelper.bean.params.oci.cfg;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * <p>
 * AddCfgParams
 * </p >
 *
 * @author yohann
 * @since 2024/11/13 14:30
 */
@Data
public class AddCfgParams {

    @NotBlank(message = "配置名称不能为空")
    private String username;

    @NotBlank(message = "配置不能为空")
    private String ociCfgStr;

    @NotNull(message = "私钥不能为空")
    private MultipartFile file;
}
