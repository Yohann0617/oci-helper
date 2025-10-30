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
import com.oracle.bmc.identitydomains.model.PasswordPolicy;
import com.yohann.ocihelper.bean.constant.CacheConstant;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.oci.tenant.GetTenantInfoParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdatePwdExpirationPolicyParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserBasicParams;
import com.yohann.ocihelper.bean.params.oci.tenant.UpdateUserInfoParams;
import com.yohann.ocihelper.bean.response.oci.tenant.TenantInfoRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.service.ITenantService;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import com.yohann.ocihelper.utils.OciUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
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
    @Resource
    private ExecutorService virtualExecutor;
    @Resource
    private CustomExpiryGuavaCache<String, Object> customCache;

    @Override
    public TenantInfoRsp tenantInfo(GetTenantInfoParams params) {
        if (params.isCleanReLaunch()) {
            customCache.remove(CacheConstant.PREFIX_TENANT_INFO + params.getOciCfgId());
        }

        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        if (StrUtil.isNotBlank(params.getRegion())) {
            SysUserDTO.OciCfg ociCfg = sysUserDTO.getOciCfg();
            ociCfg.setRegion(params.getRegion());
            sysUserDTO.setOciCfg(ociCfg);
        }

        TenantInfoRsp tenantInfoInCache = (TenantInfoRsp) customCache.get(CacheConstant.PREFIX_TENANT_INFO + params.getOciCfgId());
        if (tenantInfoInCache != null) {
            return tenantInfoInCache;
        }

        TenantInfoRsp rsp = new TenantInfoRsp();
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            IdentityClient identityClient = fetcher.getIdentityClient();
            Tenancy tenancy = identityClient.getTenancy(GetTenancyRequest.builder()
                    .tenancyId(sysUserDTO.getOciCfg().getTenantId())
                    .build()).getTenancy();
            BeanUtils.copyProperties(tenancy, rsp);

            CompletableFuture<List<TenantInfoRsp.TenantUserInfo>> userListTask = CompletableFuture.supplyAsync(() ->
                            Optional.ofNullable(identityClient.listUsers(ListUsersRequest.builder()
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
                                    }).collect(Collectors.toList()), virtualExecutor)
                    .exceptionally(e -> {
                        log.error("get user list error", e);
                        return Collections.emptyList();
                    });

            CompletableFuture<List<String>> regionsTask = CompletableFuture.supplyAsync(() ->
                            identityClient.listRegionSubscriptions(ListRegionSubscriptionsRequest.builder()
                                            .tenancyId(sysUserDTO.getOciCfg().getTenantId())
                                            .build()).getItems().stream()
                                    .map(RegionSubscription::getRegionName)
                                    .collect(Collectors.toList()), virtualExecutor)
                    .exceptionally(e -> {
                        log.error("get region list error", e);
                        return Collections.emptyList();
                    });

            CompletableFuture<PasswordPolicy> pwdExpTask = CompletableFuture.supplyAsync(() -> {
                        List<PasswordPolicy> passwordPolicyList = OciUtils.getCurrentPasswordPolicy(fetcher);
                        return passwordPolicyList.parallelStream()
                                .filter(x -> x.getPasswordExpiresAfter() != null && x.getPasswordExpiresAfter() >= 0)
                                .findAny()
                                .orElse(PasswordPolicy.builder().build());
                    }, virtualExecutor)
                    .exceptionally(e -> {
                        log.error("get pwd expires after error", e);
                        return PasswordPolicy.builder().build();
                    });

            CompletableFuture.allOf(userListTask, regionsTask, pwdExpTask).join();

            rsp.setUserList(CommonUtils.safeJoin(userListTask, Collections.emptyList()));
            rsp.setRegions(CommonUtils.safeJoin(regionsTask, Collections.emptyList()));
            rsp.setPasswordExpiresAfter(CommonUtils.safeJoin(pwdExpTask, PasswordPolicy.builder().build()).getPasswordExpiresAfter());
            return rsp;
        } catch (Exception e) {
            log.error("获取租户信息失败", e);
            throw new OciException(-1, "获取租户信息失败", e);
        } finally {
            customCache.put(CacheConstant.PREFIX_TENANT_INFO + params.getOciCfgId(), rsp, 10 * 60 * 1000);
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

    @Override
    public void updatePwdExpirationPolicy(UpdatePwdExpirationPolicyParams params) {
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            if (params.getExpirationDays() == null || params.getExpirationDays() == 0) {
                OciUtils.disablePasswordExpirationWithAutoDomain(fetcher);
            } else {
                OciUtils.enablePasswordExpirationWithAutoDomain(fetcher, params.getExpirationDays());
            }
        } catch (Exception e) {
            log.error("更新密码策略失败", e);
            throw new OciException(-1, "更新密码策略失败");
        }
    }
}
