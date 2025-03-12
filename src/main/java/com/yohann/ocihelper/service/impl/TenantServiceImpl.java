package com.yohann.ocihelper.service.impl;

import cn.hutool.core.util.StrUtil;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.model.RegionSubscription;
import com.oracle.bmc.identity.model.Tenancy;
import com.oracle.bmc.identity.requests.GetTenancyRequest;
import com.oracle.bmc.identity.requests.ListRegionSubscriptionsRequest;
import com.oracle.bmc.identity.requests.ListUsersRequest;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.oci.tenant.GetTenantInfoParams;
import com.yohann.ocihelper.bean.response.oci.tenant.TenantInfoRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.service.ITenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.stream.Collectors;

/**
 * @ClassName TenantServiceImpl
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-12 16:02
 **/
@Service
@Slf4j
public class TenantServiceImpl implements ITenantService {

    @Resource
    private ISysService sysService;

    @Override
    public TenantInfoRsp tenantInfo(GetTenantInfoParams params) {
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        if (StrUtil.isNotBlank(params.getRegion())) {
            SysUserDTO.OciCfg ociCfg = sysUserDTO.getOciCfg();
            ociCfg.setRegion(params.getRegion());
            sysUserDTO.setOciCfg(ociCfg);
        }

        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            TenantInfoRsp rsp = new TenantInfoRsp();
            IdentityClient identityClient = fetcher.getIdentityClient();
            Tenancy tenancy = identityClient.getTenancy(GetTenancyRequest.builder()
                    .tenancyId(sysUserDTO.getOciCfg().getTenantId())
                    .build()).getTenancy();
            BeanUtils.copyProperties(tenancy, rsp);
            rsp.setUserList(identityClient.listUsers(ListUsersRequest.builder()
                    .compartmentId(fetcher.getCompartmentId())
                    .build()).getItems());
            rsp.setRegions(identityClient.listRegionSubscriptions(ListRegionSubscriptionsRequest.builder()
                            .tenancyId(sysUserDTO.getOciCfg().getTenantId())
                            .build()).getItems().stream()
                    .map(RegionSubscription::getRegionName)
                    .collect(Collectors.toList()));
            return rsp;
        } catch (Exception e) {
            log.error("获取租户信息失败", e);
            throw new OciException(-1, "获取租户信息失败", e);
        }
    }
}
