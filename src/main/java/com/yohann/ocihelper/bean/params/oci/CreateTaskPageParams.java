package com.yohann.ocihelper.bean.params.oci;

import lombok.Data;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params
 * @className: CreateTaskPageParams
 * @author: Yohann
 * @date: 2024/11/15 21:37
 */
@Data
public class CreateTaskPageParams {

    private String keyword;
    private long currentPage;
    private long pageSize;
    private String architecture;
}
