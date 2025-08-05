package com.yohann.ocihelper.bean.params;

import lombok.Data;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params
 * @className: BasicPageParams
 * @author: Yohann
 * @date: 2025/3/3 21:01
 */
@Data
public class BasicPageParams {

    private String keyword;
    private int currentPage;
    private int pageSize;

    public long getOffset() {
        return (long) (currentPage - 1) * pageSize;
    }
}
