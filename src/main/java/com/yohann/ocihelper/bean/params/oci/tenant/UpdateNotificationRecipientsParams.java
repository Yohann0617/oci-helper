package com.yohann.ocihelper.bean.params.oci.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * @ClassName UpdateNotificationRecipientsParams
 * @Description: Parameters for updating domain notification recipients
 * @Author: Yohann_Fan
 * @CreateTime: 2026-04-13
 **/
@Data
public class UpdateNotificationRecipientsParams {

    @NotBlank(message = "cfgId不能为空")
    private String cfgId;

    @NotEmpty(message = "收件人邮箱列表不能为空")
    private List<String> recipients;
}
