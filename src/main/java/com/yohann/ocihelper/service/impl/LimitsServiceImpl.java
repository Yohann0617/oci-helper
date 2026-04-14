package com.yohann.ocihelper.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.oracle.bmc.limits.model.LimitDefinitionSummary;
import com.oracle.bmc.limits.model.LimitValueSummary;
import com.oracle.bmc.limits.model.ResourceAvailability;
import com.oracle.bmc.limits.model.ServiceSummary;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.oci.limits.GetLimitsParams;
import com.yohann.ocihelper.bean.response.oci.limits.GetLimitsRsp;
import com.yohann.ocihelper.bean.response.oci.limits.LimitItemRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.ILimitsService;
import com.yohann.ocihelper.service.ISysService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of {@link ILimitsService} that queries OCI service limits
 * using the OCI Limits SDK.
 *
 * @author Yohann
 */
@Service
@Slf4j
public class LimitsServiceImpl implements ILimitsService {

    @Resource
    private ISysService sysService;

    @Override
    public GetLimitsRsp getLimits(GetLimitsParams params) {
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId(), params.getRegion(), null);
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            List<LimitDefinitionSummary> definitions =
                    fetcher.listLimitDefinitions(params.getServiceName());

            List<LimitItemRsp> items = new ArrayList<>();
            for (LimitDefinitionSummary def : definitions) {
                String svcName = def.getServiceName();
                String limitName = def.getName();
                String scopeType = Optional.ofNullable(def.getScopeType())
                        .map(LimitDefinitionSummary.ScopeType::getValue)
                        .orElse("REGION");

                // Fetch current values (may return multiple rows for AD-scoped limits)
                List<LimitValueSummary> values;
                try {
                    values = fetcher.listLimitValues(svcName, limitName);
                } catch (Exception e) {
                    log.warn("Failed to get limit values for [{}/{}]: {}", svcName, limitName, e.getMessage());
                    values = Collections.emptyList();
                }

                if (CollectionUtil.isEmpty(values)) {
                    // No value data — still add a placeholder row
                    LimitItemRsp item = new LimitItemRsp();
                    item.setServiceName(svcName);
                    item.setLimitName(limitName);
                    item.setDescription(def.getDescription());
                    item.setScopeType(scopeType);
                    items.add(item);
                } else {
                    for (LimitValueSummary val : values) {
                        LimitItemRsp item = new LimitItemRsp();
                        item.setServiceName(svcName);
                        item.setLimitName(limitName);
                        item.setDescription(def.getDescription());
                        item.setScopeType(scopeType);
                        item.setAvailabilityDomain(val.getAvailabilityDomain());
                        item.setServiceLimit(val.getValue());

                        // Try to get availability (used / available)
                        try {
                            ResourceAvailability avail = fetcher.getResourceAvailability(
                                    svcName, limitName, val.getAvailabilityDomain());
                            item.setUsed(avail.getUsed());
                            item.setAvailable(avail.getAvailable());
                        } catch (Exception e) {
                            log.debug("Cannot get availability for [{}/{}]: {}", svcName, limitName, e.getMessage());
                        }
                        items.add(item);
                    }
                }
            }

            GetLimitsRsp rsp = new GetLimitsRsp();
            rsp.setTotal(items.size());
            rsp.setItems(items);
            return rsp;
        } catch (OciException oe) {
            throw oe;
        } catch (Exception e) {
            log.error("查询配额失败", e);
            throw new OciException(-1, "查询配额失败：" + e.getMessage());
        }
    }

    @Override
    public List<String> getServiceNames(String ociCfgId, String region) {
        SysUserDTO sysUserDTO = sysService.getOciUser(ociCfgId, region, null);
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            List<ServiceSummary> services = fetcher.listServices();
            return services.stream()
                    .map(ServiceSummary::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .sorted()
                    .collect(Collectors.toList());
        } catch (OciException oe) {
            throw oe;
        } catch (Exception e) {
            log.error("获取服务列表失败", e);
            throw new OciException(-1, "获取服务列表失败：" + e.getMessage());
        }
    }
}
