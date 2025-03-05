package com.yohann.ocihelper.bean.params.oci.cfg;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params
 * @className: UploadCfgParams
 * @author: Yohann
 * @date: 2024/11/18 22:11
 */
@Data
public class UploadCfgParams {

    @NotEmpty(message = "文件列表不能为空")
    private List<MultipartFile> fileList;
}
