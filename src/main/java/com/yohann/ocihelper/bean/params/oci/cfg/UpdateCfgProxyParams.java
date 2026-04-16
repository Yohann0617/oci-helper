package com.yohann.ocihelper.bean.params.oci.cfg;

import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量更新 OCI 配置专属代理的请求参数
 *
 * @author yohann
 */
@Data
public class UpdateCfgProxyParams {

    /** 需要更新的配置 ID 列表 */
    @NotEmpty(message = "配置 ID 列表不能为空")
    private List<String> idList;

    /**
     * 代理地址，支持 http://host:port 或 socks5://host:port 格式。
     * 传空字符串或 null 表示清除该配置的专属代理，届时将降级使用全局代理。
     */
    private String proxy;
}
