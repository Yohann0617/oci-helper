package com.yohann.ocihelper.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.oracle.bmc.core.model.*;
import com.oracle.bmc.core.requests.UpdateSecurityListRequest;
import com.yohann.ocihelper.bean.Tuple2;
import com.yohann.ocihelper.bean.constant.CacheConstant;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.oci.securityrule.*;
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

import jakarta.annotation.Resource;
import java.util.*;
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

        if (CollectionUtil.isEmpty(ingressSecurityRuleList) || CollectionUtil.isEmpty(egressSecurityRuleList) ||
                CollectionUtil.isEmpty(ingressMap) || CollectionUtil.isEmpty(egressMap)) {
            try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
                SecurityList securityList = fetcher.listSecurityRule(fetcher.getVcnById(params.getVcnId()));
                ingressSecurityRuleList = securityList.getIngressSecurityRules();
                egressSecurityRuleList = securityList.getEgressSecurityRules();

                ingressMap = new HashMap<>();
                egressMap = new HashMap<>();

                if (params.getType().equals(0)) {
                    Map<String, IngressSecurityRule> finalIngressMap = ingressMap;
                    rspRuleList = ingressSecurityRuleList.parallelStream().map(ingressSecurityRule -> {
                        SecurityRuleListRsp.SecurityRuleInfo info = new SecurityRuleListRsp.SecurityRuleInfo();
                        String ruleId = IdUtil.getSnowflakeNextIdStr();
                        info.setId(ruleId);
                        info.setIsStateless(ingressSecurityRule.getIsStateless());
                        info.setProtocol(SecurityRuleProtocolEnum.fromCode(ingressSecurityRule.getProtocol()).getDesc());
                        info.setSourceOrDestination(ingressSecurityRule.getSource());
                        info.setTypeAndCode(ingressSecurityRule.getProtocol().equals("1") ? (null == ingressSecurityRule.getIcmpOptions() ? "全部" :
                                ingressSecurityRule.getIcmpOptions().getType() +
                                        (null == ingressSecurityRule.getIcmpOptions().getCode() ? "" :
                                                ", " + ingressSecurityRule.getIcmpOptions().getCode())) : null);
                        info.setDescription(ingressSecurityRule.getDescription());
                        List<String> protocolList = Arrays.asList("6", "17");
                        String sourcePort = protocolList.contains(ingressSecurityRule.getProtocol()) ? "全部" : null;
                        String destinationPort = protocolList.contains(ingressSecurityRule.getProtocol()) ? "全部" : null;
                        if ("6".equals(ingressSecurityRule.getProtocol())) {
                            if (null != ingressSecurityRule.getTcpOptions()) {
                                if (null != ingressSecurityRule.getTcpOptions().getSourcePortRange()) {
                                    Integer sourceMin = ingressSecurityRule.getTcpOptions().getSourcePortRange().getMin();
                                    Integer sourceMax = ingressSecurityRule.getTcpOptions().getSourcePortRange().getMax();
                                    sourcePort = sourceMin.equals(sourceMax) ? String.valueOf(sourceMin) : sourceMin + "-" + sourceMax;
                                }
                                if (null != ingressSecurityRule.getTcpOptions().getDestinationPortRange()) {
                                    Integer dstMin = ingressSecurityRule.getTcpOptions().getDestinationPortRange().getMin();
                                    Integer dstMax = ingressSecurityRule.getTcpOptions().getDestinationPortRange().getMax();
                                    destinationPort = dstMin.equals(dstMax) ? String.valueOf(dstMin) : dstMin + "-" + dstMax;
                                }
                            }
                        }
                        if ("17".equals(ingressSecurityRule.getProtocol())) {
                            if (null != ingressSecurityRule.getUdpOptions()) {
                                if (null != ingressSecurityRule.getUdpOptions().getSourcePortRange()) {
                                    Integer sourceMin = ingressSecurityRule.getUdpOptions().getSourcePortRange().getMin();
                                    Integer sourceMax = ingressSecurityRule.getUdpOptions().getSourcePortRange().getMax();
                                    sourcePort = sourceMin.equals(sourceMax) ? String.valueOf(sourceMin) : sourceMin + "-" + sourceMax;
                                }
                                if (null != ingressSecurityRule.getUdpOptions().getDestinationPortRange()) {
                                    Integer dstMin = ingressSecurityRule.getUdpOptions().getDestinationPortRange().getMin();
                                    Integer dstMax = ingressSecurityRule.getUdpOptions().getDestinationPortRange().getMax();
                                    destinationPort = dstMin.equals(dstMax) ? String.valueOf(dstMin) : dstMin + "-" + dstMax;
                                }
                            }
                        }
                        info.setSourcePort(sourcePort);
                        info.setDestinationPort(destinationPort);

                        finalIngressMap.put(ruleId, ingressSecurityRule);
                        return info;
                    }).collect(Collectors.toList());
                }

                if (params.getType().equals(1)) {
                    Map<String, EgressSecurityRule> finalEgressMap = egressMap;
                    rspRuleList = egressSecurityRuleList.parallelStream().map(egressSecurityRule -> {
                        SecurityRuleListRsp.SecurityRuleInfo info = new SecurityRuleListRsp.SecurityRuleInfo();
                        String ruleId = IdUtil.getSnowflakeNextIdStr();
                        info.setId(ruleId);
                        info.setIsStateless(egressSecurityRule.getIsStateless());
                        info.setProtocol(SecurityRuleProtocolEnum.fromCode(egressSecurityRule.getProtocol()).getDesc());
                        info.setSourceOrDestination(egressSecurityRule.getDestination());
                        info.setTypeAndCode(egressSecurityRule.getProtocol().equals("1") ? (null == egressSecurityRule.getIcmpOptions() ? "全部" :
                                egressSecurityRule.getIcmpOptions().getType() +
                                        (null == egressSecurityRule.getIcmpOptions().getCode() ? "" :
                                                ", " + egressSecurityRule.getIcmpOptions().getCode())) : null);
                        info.setDescription(egressSecurityRule.getDescription());
                        List<String> protocolList = Arrays.asList("6", "17");
                        String sourcePort = protocolList.contains(egressSecurityRule.getProtocol()) ? "全部" : null;
                        String destinationPort = protocolList.contains(egressSecurityRule.getProtocol()) ? "全部" : null;
                        if ("6".equals(egressSecurityRule.getProtocol())) {
                            if (null != egressSecurityRule.getTcpOptions()) {
                                if (null != egressSecurityRule.getTcpOptions().getSourcePortRange()) {
                                    Integer sourceMin = egressSecurityRule.getTcpOptions().getSourcePortRange().getMin();
                                    Integer sourceMax = egressSecurityRule.getTcpOptions().getSourcePortRange().getMax();
                                    sourcePort = sourceMin.equals(sourceMax) ? String.valueOf(sourceMin) : sourceMin + "-" + sourceMax;
                                }
                                if (null != egressSecurityRule.getTcpOptions().getDestinationPortRange()) {
                                    Integer dstMin = egressSecurityRule.getTcpOptions().getDestinationPortRange().getMin();
                                    Integer dstMax = egressSecurityRule.getTcpOptions().getDestinationPortRange().getMax();
                                    destinationPort = dstMin.equals(dstMax) ? String.valueOf(dstMin) : dstMin + "-" + dstMax;
                                }
                            }
                        }
                        if ("17".equals(egressSecurityRule.getProtocol())) {
                            if (null != egressSecurityRule.getUdpOptions()) {
                                if (null != egressSecurityRule.getUdpOptions().getSourcePortRange()) {
                                    Integer sourceMin = egressSecurityRule.getUdpOptions().getSourcePortRange().getMin();
                                    Integer sourceMax = egressSecurityRule.getUdpOptions().getSourcePortRange().getMax();
                                    sourcePort = sourceMin.equals(sourceMax) ? String.valueOf(sourceMin) : sourceMin + "-" + sourceMax;
                                }
                                if (null != egressSecurityRule.getUdpOptions().getDestinationPortRange()) {
                                    Integer dstMin = egressSecurityRule.getUdpOptions().getDestinationPortRange().getMin();
                                    Integer dstMax = egressSecurityRule.getUdpOptions().getDestinationPortRange().getMax();
                                    destinationPort = dstMin.equals(dstMax) ? String.valueOf(dstMin) : dstMin + "-" + dstMax;
                                }
                            }
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
                .sorted(Comparator.comparing(SecurityRuleListRsp.SecurityRuleInfo::getSourceOrDestination))
                .collect(Collectors.toList());
        List<SecurityRuleListRsp.SecurityRuleInfo> pageList = CommonUtils.getPage(resList, params.getCurrentPage(), params.getPageSize());
        return VcnPageRsp.buildPage(pageList, params.getPageSize(), params.getCurrentPage(), resList.size());
    }

    @Override
    public void addIngress(AddIngressSecurityRuleParams params) {
        List<String> list = Arrays.asList("6", "17");
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            Vcn vcn = fetcher.getVcnById(params.getVcnId());
            UpdateSecurityRuleListParams updateSecurityRuleListParams = new UpdateSecurityRuleListParams();
            UpdateSecurityRuleListParams.IngressRule ingressRule = new UpdateSecurityRuleListParams.IngressRule();
            AddIngressSecurityRuleParams.IngressInfo inboundRule = params.getInboundRule();
            ingressRule.setIcmpOptions(inboundRule.getIcmpOptions());
            ingressRule.setIsStateless(inboundRule.getIsStateless());
            ingressRule.setProtocol(inboundRule.getProtocol());
            ingressRule.setSource(inboundRule.getSource());
            ingressRule.setSourceType(inboundRule.getSourceType());
            ingressRule.setDescription(StrUtil.isBlank(inboundRule.getDescription()) ? null : inboundRule.getDescription());
            if (list.contains(inboundRule.getProtocol())) {
                Tuple2<Integer, Integer> sourcePort = getPortRange(inboundRule.getSourcePort());
                Tuple2<Integer, Integer> destinationPort = getPortRange(inboundRule.getDestinationPort());
                if ("6".equals(inboundRule.getProtocol())) {
                    ingressRule.setTcpSourcePortMin(sourcePort.getFirst());
                    ingressRule.setTcpSourcePortMax(sourcePort.getSecond());
                    ingressRule.setTcpDesPortMin(destinationPort.getFirst());
                    ingressRule.setTcpDesPortMax(destinationPort.getSecond());
                }
                if ("17".equals(inboundRule.getProtocol())) {
                    ingressRule.setUdpSourcePortMin(sourcePort.getFirst());
                    ingressRule.setUdpSourcePortMax(sourcePort.getSecond());
                    ingressRule.setUdpDesPortMin(destinationPort.getFirst());
                    ingressRule.setUdpDesPortMax(destinationPort.getSecond());
                }
            }
            updateSecurityRuleListParams.setIngressRuleList(Collections.singletonList(ingressRule));
            fetcher.updateSecurityRuleList(vcn, updateSecurityRuleListParams);
        } catch (Exception e) {
            log.error("新增入站规则失败", e);
            throw new OciException(-1, "新增入站规则失败：" + e.getMessage());
        }
    }

    @Override
    public void addEgress(AddEgressSecurityRuleParams params) {
        List<String> list = Arrays.asList("6", "17");
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            Vcn vcn = fetcher.getVcnById(params.getVcnId());
            UpdateSecurityRuleListParams updateSecurityRuleListParams = new UpdateSecurityRuleListParams();
            UpdateSecurityRuleListParams.EgressRule egressRule = new UpdateSecurityRuleListParams.EgressRule();
            AddEgressSecurityRuleParams.EgressInfo outboundRule = params.getOutboundRule();
            egressRule.setIcmpOptions(outboundRule.getIcmpOptions());
            egressRule.setDestination(outboundRule.getDestination());
            egressRule.setDestinationType(outboundRule.getDestinationType());
            egressRule.setIsStateless(outboundRule.getIsStateless());
            egressRule.setProtocol(outboundRule.getProtocol());
            egressRule.setDescription(StrUtil.isBlank(outboundRule.getDescription()) ? null : outboundRule.getDescription());
            if (list.contains(outboundRule.getProtocol())) {
                Tuple2<Integer, Integer> sourcePort = getPortRange(outboundRule.getSourcePort());
                Tuple2<Integer, Integer> destinationPort = getPortRange(outboundRule.getDestinationPort());
                if ("6".equals(outboundRule.getProtocol())) {
                    egressRule.setTcpSourcePortMin(sourcePort.getFirst());
                    egressRule.setTcpSourcePortMax(sourcePort.getSecond());
                    egressRule.setTcpDesPortMin(destinationPort.getFirst());
                    egressRule.setTcpDesPortMax(destinationPort.getSecond());
                }
                if ("17".equals(outboundRule.getProtocol())) {
                    egressRule.setUdpSourcePortMin(sourcePort.getFirst());
                    egressRule.setUdpSourcePortMax(sourcePort.getSecond());
                    egressRule.setUdpDesPortMin(destinationPort.getFirst());
                    egressRule.setUdpDesPortMax(destinationPort.getSecond());
                }
            }
            updateSecurityRuleListParams.setEgressRuleList(Collections.singletonList(egressRule));
            fetcher.updateSecurityRuleList(vcn, updateSecurityRuleListParams);
        } catch (Exception e) {
            log.error("新增出站规则失败", e);
            throw new OciException(-1, "新增出站规则失败：" + e.getMessage());
        }
    }

    @Override
    public void remove(RemoveSecurityRuleParams params) {
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        Map<String, IngressSecurityRule> ingressMap = (Map<String, IngressSecurityRule>) customCache.get(CacheConstant.PREFIX_INGRESS_SECURITY_RULE_MAP + params.getVcnId());
        Map<String, EgressSecurityRule> egressMap = (Map<String, EgressSecurityRule>) customCache.get(CacheConstant.PREFIX_EGRESS_SECURITY_RULE_MAP + params.getVcnId());
        params.getRuleIds().forEach(ruleId -> {
            if (params.getType().equals(0)) {
                ingressMap.remove(ruleId);
            }
            if (params.getType().equals(1)) {
                egressMap.remove(ruleId);
            }
        });

        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            Vcn vcn = fetcher.getVcnById(params.getVcnId());
            SecurityList securityList = fetcher.listSecurityRule(vcn);
            List<IngressSecurityRule> ingressSecurityRules = new ArrayList<>(ingressMap.values());
            List<EgressSecurityRule> egressSecurityRules = new ArrayList<>(egressMap.values());
            if (params.getType().equals(0)) {
                egressSecurityRules = securityList.getEgressSecurityRules();
            }
            if (params.getType().equals(1)) {
                ingressSecurityRules = securityList.getIngressSecurityRules();
            }
            fetcher.getVirtualNetworkClient().updateSecurityList(UpdateSecurityListRequest.builder()
                    .securityListId(vcn.getDefaultSecurityListId())
                    .updateSecurityListDetails(UpdateSecurityListDetails.builder()
                            .ingressSecurityRules(ingressSecurityRules)
                            .egressSecurityRules(egressSecurityRules)
                            .build())
                    .build());
        } catch (Exception e) {
            log.error("删除安全规则失败", e);
            throw new OciException(-1, "删除安全规则失败");
        }
        customCache.remove(CacheConstant.PREFIX_INGRESS_SECURITY_RULE_PAGE + params.getOciCfgId());
        customCache.remove(CacheConstant.PREFIX_EGRESS_SECURITY_RULE_PAGE + params.getOciCfgId());
        customCache.remove(CacheConstant.PREFIX_INGRESS_SECURITY_RULE_MAP + params.getVcnId());
        customCache.remove(CacheConstant.PREFIX_EGRESS_SECURITY_RULE_MAP + params.getVcnId());
    }

    private Tuple2<Integer, Integer> getPortRange(String portRangeStr) {
        if (StrUtil.isBlank(portRangeStr)) {
            return Tuple2.of(null, null);
        }
        String[] split = portRangeStr.split("-");
        if (split.length == 1) {
            return Tuple2.of(Integer.valueOf(split[0]), Integer.valueOf(split[0]));
        } else if (split.length == 2) {
            return Tuple2.of(Integer.valueOf(split[0]), Integer.valueOf(split[1]));
        } else {
            throw new OciException(-1, "格式有误：" + portRangeStr);
        }
    }
}
