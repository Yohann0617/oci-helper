package com.yohann.ocihelper.service;

import com.yohann.ocihelper.bean.params.oci.tenant.GetTenantInfoParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserBasicParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserInfoParams;
import com.yohann.ocihelper.bean.response.oci.tenant.TenantInfoRsp;

/**
 * @ClassName ITenantService
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-12 16:02
 **/
public interface ITenantService {
    TenantInfoRsp tenantInfo(GetTenantInfoParams params);

    void deleteMfaDevice(UpdateUserBasicParams params);

    void deleteApiKey(UpdateUserBasicParams params);

    void resetPassword(UpdateUserBasicParams params);

    void updateUserInfo(UpdateUserInfoParams params);

    void deleteUser(UpdateUserBasicParams params);

}
