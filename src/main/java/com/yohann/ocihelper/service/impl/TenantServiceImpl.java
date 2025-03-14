package com.yohann.ocihelper.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.model.RegionSubscription;
import com.oracle.bmc.identity.model.Tenancy;
import com.oracle.bmc.identity.requests.DeleteUserRequest;
import com.oracle.bmc.identity.requests.GetTenancyRequest;
import com.oracle.bmc.identity.requests.ListRegionSubscriptionsRequest;
import com.oracle.bmc.identity.requests.ListUsersRequest;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.oci.tenant.GetTenantInfoParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserBasicParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserInfoParams;
import com.yohann.ocihelper.bean.response.oci.tenant.TenantInfoRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.service.ITenantService;
import com.yohann.ocihelper.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Optional;
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
            rsp.setUserList(Optional.ofNullable(identityClient.listUsers(ListUsersRequest.builder()
                            .compartmentId(fetcher.getCompartmentId())
                            .build()).getItems())
                    .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).stream()
                    .map(x -> {
                        TenantInfoRsp.TenantUserInfo info = new TenantInfoRsp.TenantUserInfo();
                        info.setId(x.getId());
                        info.setName(x.getName());
                        info.setEmail(x.getEmail());
                        info.setLifecycleState(x.getLifecycleState().getValue());
                        info.setEmailVerified(x.getEmailVerified());
                        info.setIsMfaActivated(x.getIsMfaActivated());
                        info.setTimeCreated(CommonUtils.dateFmt2String(x.getTimeCreated()));
                        info.setLastSuccessfulLoginTime(x.getLastSuccessfulLoginTime() == null ? null : CommonUtils.dateFmt2String(x.getLastSuccessfulLoginTime()));
                        info.setJsonStr(JSONUtil.toJsonStr(x));
                        return info;
                    }).collect(Collectors.toList()));
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

    @Override
    public void deleteMfaDevice(UpdateUserBasicParams params) {
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        if (StrUtil.isNotBlank(params.getUserId())) {
            SysUserDTO.OciCfg ociCfg = sysUserDTO.getOciCfg();
            ociCfg.setUserId(params.getUserId());
            sysUserDTO.setOciCfg(ociCfg);
        }

        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            fetcher.deleteAllMfa();
        } catch (Exception e) {
            log.error("清除 MFA 设备失败", e);
            throw new OciException(-1, "清除 MFA 设备失败", e);
        }
    }

    @Override
    public void deleteApiKey(UpdateUserBasicParams params) {
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        if (StrUtil.isNotBlank(params.getUserId())) {
            SysUserDTO.OciCfg ociCfg = sysUserDTO.getOciCfg();
            ociCfg.setUserId(params.getUserId());
            sysUserDTO.setOciCfg(ociCfg);
        }

        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            fetcher.deleteAllApiKey();
        } catch (Exception e) {
            log.error("清除所有 API 失败", e);
            throw new OciException(-1, "清除所有 API 失败", e);
        }
    }

    @Override
    public void resetPassword(UpdateUserBasicParams params) {
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        if (StrUtil.isNotBlank(params.getUserId())) {
            SysUserDTO.OciCfg ociCfg = sysUserDTO.getOciCfg();
            ociCfg.setUserId(params.getUserId());
            sysUserDTO.setOciCfg(ociCfg);
        }

        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            fetcher.createOrResetUIPassword();
        } catch (Exception e) {
            log.error("重置用户密码失败", e);
            throw new OciException(-1, "重置用户密码失败", e);
        }
    }

    @Override
    public void updateUserInfo(UpdateUserInfoParams params) {
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        if (StrUtil.isNotBlank(params.getUserId())) {
            SysUserDTO.OciCfg ociCfg = sysUserDTO.getOciCfg();
            ociCfg.setUserId(params.getUserId());
            sysUserDTO.setOciCfg(ociCfg);
        }

        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            fetcher.updateUser(params.getEmail(), params.getDbUserName(), params.getDescription());
        } catch (Exception e) {
            log.error("更新用户信息失败", e);
            throw new OciException(-1, "更新用户信息失败", e);
        }
    }

    @Override
    public void deleteUser(UpdateUserBasicParams params) {
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        if (StrUtil.isNotBlank(params.getUserId())) {
            SysUserDTO.OciCfg ociCfg = sysUserDTO.getOciCfg();
            ociCfg.setUserId(params.getUserId());
            sysUserDTO.setOciCfg(ociCfg);
        }

        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            fetcher.getIdentityClient().deleteUser(DeleteUserRequest.builder()
                    .userId(params.getUserId())
                    .build());
        } catch (Exception e) {
            log.error("删除用户失败", e);
            throw new OciException(-1, "删除用户失败", e);
        }
    }
}
