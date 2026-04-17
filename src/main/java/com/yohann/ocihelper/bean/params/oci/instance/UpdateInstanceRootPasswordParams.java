package com.yohann.ocihelper.bean.params.oci.instance;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 更新/删除实例 root 密码标签的请求参数
 * password 为空字符串或 null 时表示删除该标签
 * </p>
 *
 * @author yohann
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UpdateInstanceRootPasswordParams extends GetInstanceCfgInfoParams {

    /**
     * 新的 root 密码；传 null 或空字符串则删除 freeformTags 中的 root-password 标签
     */
    private String password;
}
