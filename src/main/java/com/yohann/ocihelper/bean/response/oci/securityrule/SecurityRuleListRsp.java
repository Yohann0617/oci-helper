package com.yohann.ocihelper.bean.response.oci.securityrule;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yohann.ocihelper.bean.response.oci.vcn.VcnPageRsp;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.response.oci.securityrule
 * @className: SecurityRuleListRsp
 * @author: Yohann
 * @date: 2025/3/1 16:04
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SecurityRuleListRsp<T> extends Page<T> {

    @Data
    public static class SecurityRuleInfo {

        private String id;
        private Boolean isStateless;
        private String protocol;
        private String sourceOrDestination;
        private String sourcePort;
        private String destinationPort;
        private String typeAndCode;
        private String description;
    }

    public static <T> Page<T> buildPage(List<T> entities, long size, long current, long total) {
        SecurityRuleListRsp<T> page = new SecurityRuleListRsp<>();
        page.setRecords(entities);
        page.setSize(size);
        page.setCurrent(current);
        page.setTotal(total);
        page.setPages((long) (Math.ceil((double) total / size)));
        return page;
    }
}
