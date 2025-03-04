package com.yohann.ocihelper.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oracle.bmc.core.model.EgressSecurityRule;
import com.oracle.bmc.core.model.IngressSecurityRule;
import com.oracle.bmc.core.model.SecurityList;
import com.yohann.ocihelper.bean.constant.CacheConstant;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.oci.securityrule.GetSecurityRuleListPageParams;
import com.yohann.ocihelper.bean.response.oci.securityrule.SecurityRuleListRsp;
import com.yohann.ocihelper.bean.response.oci.vcn.VcnPageRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.enums.SecurityRuleProtocolEnum;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.ISecurityRuleService;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.CustomExpiryGuavaCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.service.impl
 * @className: SecurityRuleServiceImpl
 * @author: Yohann
 * @date: 2025/3/1 15:38
 */
@Service
@Slf4j
public class SecurityRuleServiceImpl implements ISecurityRuleService {

    @Resource
    private ISysService sysService;
    @Resource
    private CustomExpiryGuavaCache<String, Object> customCache;

    @Override
    public Page<SecurityRuleListRsp.SecurityRuleInfo> page(GetSecurityRuleListPageParams params) {
        if (params.isCleanReLaunch()) {
            customCache.remove(CacheConstant.PREFIX_INGRESS_SECURITY_RULE_PAGE + params.getOciCfgId());
            customCache.remove(CacheConstant.PREFIX_EGRESS_SECURITY_RULE_PAGE + params.getOciCfgId());
            customCache.remove(CacheConstant.PREFIX_INGRESS_SECURITY_RULE_MAP + params.getVcnId());
            customCache.remove(CacheConstant.PREFIX_EGRESS_SECURITY_RULE_MAP + params.getVcnId());
        }

        List<IngressSecurityRule> ingressSecurityRuleList = (List<IngressSecurityRule>) customCache.get(CacheConstant.PREFIX_INGRESS_SECURITY_RULE_PAGE + params.getOciCfgId());
        List<EgressSecurityRule> egressSecurityRuleList = (List<EgressSecurityRule>) customCache.get(CacheConstant.PREFIX_EGRESS_SECURITY_RULE_PAGE + params.getOciCfgId());
        Map<String, IngressSecurityRule> ingressMap = (Map<String, IngressSecurityRule>) customCache.get(CacheConstant.PREFIX_INGRESS_SECURITY_RULE_MAP + params.getVcnId());
        Map<String, EgressSecurityRule> egressMap = (Map<String, EgressSecurityRule>) customCache.get(CacheConstant.PREFIX_EGRESS_SECURITY_RULE_MAP + params.getVcnId());

        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        List<SecurityRuleListRsp.SecurityRuleInfo> rspRuleList = Collections.emptyList();

        if (ingressSecurityRuleList.isEmpty() || egressSecurityRuleList.isEmpty() || ingressMap.isEmpty() || egressMap.isEmpty()) {
            try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
                SecurityList securityList = fetcher.listSecurityRule(fetcher.getVcnById(params.getVcnId()));
                ingressSecurityRuleList = securityList.getIngressSecurityRules();
                egressSecurityRuleList = securityList.getEgressSecurityRules();

                ingressMap = new HashMap<>();
                egressMap = new HashMap<>();

                if (params.getType().equals(0)) {
                    Map<String, IngressSecurityRule> finalIngressMap = ingressMap;
                    rspRuleList = ingressSecurityRuleList.stream().map(ingressSecurityRule -> {
                        SecurityRuleListRsp.SecurityRuleInfo info = new SecurityRuleListRsp.SecurityRuleInfo();
                        String ruleId = IdUtil.getSnowflakeNextIdStr();
                        info.setId(ruleId);
                        info.setIsStateless(ingressSecurityRule.getIsStateless());
                        info.setProtocol(SecurityRuleProtocolEnum.fromCode(ingressSecurityRule.getProtocol()).getDesc());
                        info.setSourceOrDestination(ingressSecurityRule.getSource());
                        info.setTypeAndCode(ingressSecurityRule.getIcmpOptions().getType() + "," + ingressSecurityRule.getIcmpOptions().getCode());
                        info.setDescription(ingressSecurityRule.getDescription());
                        String sourcePort = null;
                        String destinationPort = null;
                        if ("6".equals(ingressSecurityRule.getProtocol())) {
                            Integer sourceMin = ingressSecurityRule.getTcpOptions().getSourcePortRange().getMin();
                            Integer sourceMax = ingressSecurityRule.getTcpOptions().getSourcePortRange().getMax();
                            sourcePort = sourceMin.equals(sourceMax) ? String.valueOf(sourceMin) : sourceMin + "-" + sourceMax;
                            Integer dstMin = ingressSecurityRule.getTcpOptions().getDestinationPortRange().getMin();
                            Integer dstMax = ingressSecurityRule.getTcpOptions().getDestinationPortRange().getMax();
                            destinationPort = dstMin.equals(dstMax) ? String.valueOf(dstMin) : dstMin + "-" + dstMax;
                        }
                        if ("17".equals(ingressSecurityRule.getProtocol())) {
                            Integer sourceMin = ingressSecurityRule.getUdpOptions().getSourcePortRange().getMin();
                            Integer sourceMax = ingressSecurityRule.getUdpOptions().getSourcePortRange().getMax();
                            sourcePort = sourceMin.equals(sourceMax) ? String.valueOf(sourceMin) : sourceMin + "-" + sourceMax;
                            Integer dstMin = ingressSecurityRule.getUdpOptions().getDestinationPortRange().getMin();
                            Integer dstMax = ingressSecurityRule.getUdpOptions().getDestinationPortRange().getMax();
                            destinationPort = dstMin.equals(dstMax) ? String.valueOf(dstMin) : dstMin + "-" + dstMax;
                        }
                        info.setSourcePort(sourcePort);
                        info.setDestinationPort(destinationPort);

                        finalIngressMap.put(ruleId, ingressSecurityRule);
                        return info;
                    }).collect(Collectors.toList());
                }

                if (params.getType().equals(1)) {
                    Map<String, EgressSecurityRule> finalEgressMap = egressMap;
                    rspRuleList = egressSecurityRuleList.stream().map(egressSecurityRule -> {
                        SecurityRuleListRsp.SecurityRuleInfo info = new SecurityRuleListRsp.SecurityRuleInfo();
                        String ruleId = IdUtil.getSnowflakeNextIdStr();
                        info.setId(ruleId);
                        info.setIsStateless(egressSecurityRule.getIsStateless());
                        info.setProtocol(SecurityRuleProtocolEnum.fromCode(egressSecurityRule.getProtocol()).getDesc());
                        info.setSourceOrDestination(egressSecurityRule.getDestination());
                        info.setTypeAndCode(egressSecurityRule.getIcmpOptions().getType() + "," + egressSecurityRule.getIcmpOptions().getCode());
                        info.setDescription(egressSecurityRule.getDescription());
                        String sourcePort = null;
                        String destinationPort = null;
                        if ("6".equals(egressSecurityRule.getProtocol())) {
                            Integer sourceMin = egressSecurityRule.getTcpOptions().getSourcePortRange().getMin();
                            Integer sourceMax = egressSecurityRule.getTcpOptions().getSourcePortRange().getMax();
                            sourcePort = sourceMin.equals(sourceMax) ? String.valueOf(sourceMin) : sourceMin + "-" + sourceMax;
                            Integer dstMin = egressSecurityRule.getTcpOptions().getDestinationPortRange().getMin();
                            Integer dstMax = egressSecurityRule.getTcpOptions().getDestinationPortRange().getMax();
                            destinationPort = dstMin.equals(dstMax) ? String.valueOf(dstMin) : dstMin + "-" + dstMax;
                        }
                        if ("17".equals(egressSecurityRule.getProtocol())) {
                            Integer sourceMin = egressSecurityRule.getUdpOptions().getSourcePortRange().getMin();
                            Integer sourceMax = egressSecurityRule.getUdpOptions().getSourcePortRange().getMax();
                            sourcePort = sourceMin.equals(sourceMax) ? String.valueOf(sourceMin) : sourceMin + "-" + sourceMax;
                            Integer dstMin = egressSecurityRule.getUdpOptions().getDestinationPortRange().getMin();
                            Integer dstMax = egressSecurityRule.getUdpOptions().getDestinationPortRange().getMax();
                            destinationPort = dstMin.equals(dstMax) ? String.valueOf(dstMin) : dstMin + "-" + dstMax;
                        }
                        info.setSourcePort(sourcePort);
                        info.setDestinationPort(destinationPort);

                        finalEgressMap.put(ruleId, egressSecurityRule);
                        return info;
                    }).collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.error("获取安全列表规则失败", e);
                throw new OciException(-1, "获取安全列表规则失败");
            }
        }

        customCache.put(CacheConstant.PREFIX_INGRESS_SECURITY_RULE_PAGE + params.getOciCfgId(), ingressSecurityRuleList, 10 * 60 * 1000);
        customCache.put(CacheConstant.PREFIX_EGRESS_SECURITY_RULE_PAGE + params.getOciCfgId(), egressSecurityRuleList, 10 * 60 * 1000);
        customCache.put(CacheConstant.PREFIX_INGRESS_SECURITY_RULE_MAP + params.getVcnId(), ingressMap, 10 * 60 * 1000);
        customCache.put(CacheConstant.PREFIX_EGRESS_SECURITY_RULE_MAP + params.getVcnId(), egressMap, 10 * 60 * 1000);

        List<SecurityRuleListRsp.SecurityRuleInfo> resList = rspRuleList.parallelStream()
                .filter(x -> CommonUtils.contains(x.getSourceOrDestination(), params.getKeyword(), true) ||
                        CommonUtils.contains(x.getDescription(), params.getKeyword(), true) ||
                        CommonUtils.contains(x.getSourcePort(), params.getKeyword(), true) ||
                        CommonUtils.contains(x.getDestinationPort(), params.getKeyword(), true))
                .collect(Collectors.toList());
        List<SecurityRuleListRsp.SecurityRuleInfo> pageList = CommonUtils.getPage(resList, params.getCurrentPage(), params.getPageSize());
        return VcnPageRsp.buildPage(pageList, params.getPageSize(), params.getCurrentPage(), pageList.size());
    }
}
