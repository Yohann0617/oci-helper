package com.yohann.ocihelper.bean.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.dto
 * @className: GoogleLoginConfigDTO
 * @author: Yohann
 * @date: 2026/01/02
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginConfigDTO {

    /**
     * 是否启用Google一键登录
     */
    private Boolean enabled;

    /**
     * Google OAuth客户端ID
     */
    private String clientId;

    /**
     * 允许登录的Google账号列表（逗号分隔，必须是完整邮箱地址）
     * 例如：user1@gmail.com,user2@company.com,admin@example.com
     */
    private String allowedEmails;
}
