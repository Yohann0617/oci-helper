package com.yohann.ocihelper.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.Vnic;
import com.oracle.bmc.identity.model.RegionSubscription;
import com.oracle.bmc.monitoring.model.SummarizeMetricsDataDetails;
import com.oracle.bmc.monitoring.requests.SummarizeMetricsDataRequest;
import com.oracle.bmc.monitoring.responses.SummarizeMetricsDataResponse;
import com.yohann.ocihelper.bean.Tuple2;
import com.yohann.ocihelper.bean.constant.CacheConstant;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.dto.ValueLabelDTO;
import com.yohann.ocihelper.bean.params.oci.traffic.GetTrafficDataParams;
import com.yohann.ocihelper.bean.response.oci.traffic.FetchInstancesRsp;
import com.yohann.ocihelper.bean.response.oci.traffic.GetConditionRsp;
import com.yohann.ocihelper.bean.response.oci.traffic.GetTrafficDataRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.service.ITrafficService;
import com.yohann.ocihelper.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.service.impl
 * @className: TrafficServiceImpl
 * @author: Yohann
 * @date: 2025/3/7 21:28
 */
@Service
@Slf4j
public class TrafficServiceImpl implements ITrafficService {

    @Resource
    private ISysService sysService;

    @Override
    public GetTrafficDataRsp getData(GetTrafficDataParams params) {
        checkTrafficQuery(params.getInQuery(), params.getOutQuery());
        
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        SysUserDTO.OciCfg ociCfg = sysUserDTO.getOciCfg();
        ociCfg.setRegion(params.getRegion());
        sysUserDTO.setOciCfg(ociCfg);
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            List<ValueLabelDTO> inTrafficData = getTrafficData(fetcher, CacheConstant.OCI_TRAFFIC_NAMESPACE,
                    params.getInQuery(), params.getBeginTime(), params.getEndTime());
            List<ValueLabelDTO> outTrafficData = getTrafficData(fetcher, CacheConstant.OCI_TRAFFIC_NAMESPACE,
                    params.getOutQuery(), params.getBeginTime(), params.getEndTime());
            GetTrafficDataRsp rsp = new GetTrafficDataRsp();
            rsp.setTime(inTrafficData.stream().map(ValueLabelDTO::getLabel).collect(Collectors.toList()));
            rsp.setInbound(inTrafficData.stream()
                    .map(x -> CommonUtils.formatBytes(Long.parseLong(x.getValue()), "MB"))
                    .collect(Collectors.toList()));
            rsp.setOutbound(outTrafficData.stream()
                    .map(x -> CommonUtils.formatBytes(Long.parseLong(x.getValue()), "MB"))
                    .collect(Collectors.toList()));
            return rsp;
        } catch (Exception e) {
            log.error("获取数据失败", e);
            throw new OciException(-1, "获取数据失败：" + e.getMessage());
        }
    }
    private void checkTrafficQuery(String inQuery, String outQuery) {
        if (inQuery == null || outQuery == null) {
            throw new OciException(-1, "获取数据失败：请求参数为空");
        }
        boolean missingResourceId = !inQuery.contains("resourceId = \"ocid1") || !outQuery.contains("resourceId = \"ocid1");
        if (missingResourceId) {
            throw new OciException(-1, "获取数据失败：请先选择实例和VNIC");
        }
        boolean missingTimeRange = !inQuery.contains("[") || !outQuery.contains("[");
        if (missingTimeRange) {
            throw new OciException(-1, "获取数据失败：请先选择时间间隔");
        }
    }

    @Override
    public GetConditionRsp getCondition(String ociCfgId) {
        SysUserDTO sysUserDTO = sysService.getOciUser(ociCfgId);
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            GetConditionRsp rsp = new GetConditionRsp();
            List<RegionSubscription> regionSubscriptionList = fetcher.listRegionSubscriptions();
            rsp.setRegionOptions(Optional.ofNullable(regionSubscriptionList)
                    .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).stream()
                    .map(x -> new ValueLabelDTO(x.getRegionName(), x.getRegionName()))
                    .collect(Collectors.toList()));
            rsp.setInstanceOptions(Optional.ofNullable(regionSubscriptionList)
                    .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
                    .map(x -> {
                        SysUserDTO.OciCfg ociCfg = sysUserDTO.getOciCfg();
                        ociCfg.setRegion(x.getRegionName());
                        sysUserDTO.setOciCfg(ociCfg);
                        try (OracleInstanceFetcher otherFetcher = new OracleInstanceFetcher(sysUserDTO)) {
                            List<ValueLabelDTO> instances = Optional.ofNullable(otherFetcher.listInstances())
                                    .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
                                    .map(y -> new ValueLabelDTO(y.getDisplayName(), y.getId()))
                                    .collect(Collectors.toList());
                            return Tuple2.of(x.getRegionName(), instances);
                        } catch (Exception e) {
                            log.error("获取区域实例失败", e);
                            throw new OciException(-1, "获取区域实例失败：" + e.getMessage());
                        }
                    })
                    .collect(Collectors.toMap(Tuple2::getFirst, Tuple2::getSecond)));
            return rsp;
        } catch (Exception e) {
            log.error("获取区域失败", e);
            throw new OciException(-1, "获取区域失败：" + e.getMessage());
        }
    }

    @Override
    public FetchInstancesRsp fetchInstances(String ociCfgId, String region) {
        SysUserDTO sysUserDTO = sysService.getOciUser(ociCfgId);
        SysUserDTO.OciCfg ociCfg = sysUserDTO.getOciCfg();
        ociCfg.setRegion(region);
        sysUserDTO.setOciCfg(ociCfg);
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            FetchInstancesRsp rsp = new FetchInstancesRsp();
            List<Instance> instanceList = fetcher.listInstances();
            rsp.setInstanceCount(instanceList.size());
            rsp.setInboundTraffic(CommonUtils.formatBytes(Optional.ofNullable(instanceList)
                    .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
                    .map(x -> fetcher.listVnicByInstanceId(x.getId()).parallelStream().map(y ->
                                    getTrafficData(fetcher, CacheConstant.OCI_TRAFFIC_NAMESPACE,
                                            String.format(CacheConstant.OCI_TRAFFIC_QUERY_IN, y.getId()),
                                            CommonUtils.localDateTime2Date(CommonUtils.getMonthFirstDayFirstSecond()),
                                            CommonUtils.localDateTime2Date(CommonUtils.getMonthLastDayLastSecond())))
                            .flatMap(Collection::stream)
                            .collect(Collectors.toList()))
                    .flatMap(Collection::stream)
                    .map(ValueLabelDTO::getValue)
                    .filter(Objects::nonNull)
                    .filter(v -> v.matches("\\d+"))
                    .mapToLong(Long::parseLong)
                    .sum()));
            rsp.setOutboundTraffic(CommonUtils.formatBytes(Optional.ofNullable(instanceList)
                    .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
                    .map(x -> fetcher.listVnicByInstanceId(x.getId()).parallelStream().map(y ->
                                    getTrafficData(fetcher, CacheConstant.OCI_TRAFFIC_NAMESPACE,
                                            String.format(CacheConstant.OCI_TRAFFIC_QUERY_OUT, y.getId()),
                                            CommonUtils.localDateTime2Date(CommonUtils.getMonthFirstDayFirstSecond()),
                                            CommonUtils.localDateTime2Date(CommonUtils.getMonthLastDayLastSecond())))
                            .flatMap(Collection::stream)
                            .collect(Collectors.toList()))
                    .flatMap(Collection::stream)
                    .map(ValueLabelDTO::getValue)
                    .filter(Objects::nonNull)
                    .filter(v -> v.matches("\\d+"))
                    .mapToLong(Long::parseLong)
                    .sum()));
            return rsp;
        } catch (Exception e) {
            log.error("获取区域实例失败", e);
            throw new OciException(-1, "获取区域实例失败：" + e.getMessage());
        }
    }

    @Override
    public List<ValueLabelDTO> fetchVnics(String ociCfgId, String region, String instanceId) {
        SysUserDTO sysUserDTO = sysService.getOciUser(ociCfgId);
        SysUserDTO.OciCfg ociCfg = sysUserDTO.getOciCfg();
        ociCfg.setRegion(region);
        sysUserDTO.setOciCfg(ociCfg);
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            List<Vnic> vnicList = fetcher.listVnicByInstanceId(instanceId);
            return Optional.ofNullable(vnicList)
                    .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
                    .map(x -> new ValueLabelDTO(x.getDisplayName(), x.getId()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取区域实例vnic失败", e);
            throw new OciException(-1, "获取区域实例vnic失败：" + e.getMessage());
        }
    }


    private List<ValueLabelDTO> getTrafficData(OracleInstanceFetcher fetcher,
                                               String namespace,
                                               String query,
                                               Date beginTime, Date endTime) {
        SummarizeMetricsDataResponse summarizeMetricsDataResponse = fetcher.getMonitoringClient()
                .summarizeMetricsData(SummarizeMetricsDataRequest.builder()
                        .compartmentId(fetcher.getCompartmentId())
                        .summarizeMetricsDataDetails(SummarizeMetricsDataDetails.builder()
                                .namespace(namespace)
                                .query(query)
                                .startTime(beginTime)
                                .endTime(endTime)
                                .build())
                        .build());
        return Optional.ofNullable(summarizeMetricsDataResponse.getItems())
                .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
                .map(x -> Optional.ofNullable(x.getAggregatedDatapoints())
                        .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
                        .map(y -> new ValueLabelDTO(CommonUtils.dateFmt2String(y.getTimestamp()),
                                (y.getValue().longValue()) + ""))
                        .collect(Collectors.toList()))
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(ValueLabelDTO::getLabel))
                .collect(Collectors.toList());
    }
}
