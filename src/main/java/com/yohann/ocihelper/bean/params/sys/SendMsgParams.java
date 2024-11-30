package com.yohann.ocihelper.bean.params.sys;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params.sys
 * @className: SendMsgParams
 * @author: Yohann
 * @date: 2024/11/30 18:32
 */
@Data
public class SendMsgParams {

    @NotBlank(message = "消息不能为空")
    private String message;
}
