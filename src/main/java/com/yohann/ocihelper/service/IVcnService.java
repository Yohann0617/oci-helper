package com.yohann.ocihelper.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yohann.ocihelper.bean.params.oci.vcn.RemoveVcnParams;
import com.yohann.ocihelper.bean.params.oci.vcn.VcnPageParams;
import com.yohann.ocihelper.bean.response.oci.vcn.VcnPageRsp;

public interface IVcnService {

    Page<VcnPageRsp.VcnInfo> page(VcnPageParams params);

    void remove(RemoveVcnParams params);
}
