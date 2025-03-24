package com.yohann.ocihelper.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yohann.ocihelper.bean.dto.CfDnsRecordDTO;
import com.yohann.ocihelper.bean.params.cf.*;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.ICfApiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @ClassName CfServiceImpl
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-19 14:33
 **/
@Service
@Slf4j
public class CfApiServiceImpl implements ICfApiService {


    @Override
    public HttpResponse addCfDnsRecords(AddCfDnsRecordsParams params) {
        String url = String.format("https://api.cloudflare.com/client/v4/zones/%s/dns_records", params.getZoneId());
        JSONObject data = new JSONObject();
        data.set("type", params.getType());
        data.set("name", params.getDomainPrefix());
        data.set("content", params.getIpAddress());
        data.set("proxied", params.isProxied());
        data.set("ttl", params.getTtl());
        data.set("comment", params.getComment());
        return HttpRequest.post(url)
                .header("Authorization", "Bearer " + params.getApiToken())
                .header("Content-Type", "application/json")
                .body(data.toString())
                .execute();
    }

    @Override
    public void removeCfDnsRecords(RemoveCfDnsRecordsParams params) {
        params.getProxyDomainList().parallelStream().forEach(proxyDomain -> {
            String url = "https://api.cloudflare.com/client/v4/zones/" + params.getZoneId() + "/dns_records?type=A&name=" + proxyDomain;
            HttpRequest request = HttpRequest.get(url)
                    .header("Authorization", "Bearer " + params.getApiToken())
                    .header("Content-Type", "application/json");
            HttpResponse response = request.execute();
            String responseBody = response.body();
            JSONArray recordsArray = JSONUtil.parseArray(JSONUtil.parseObj(responseBody).get("result"));
            for (int i = 0; i < recordsArray.size(); i++) {
                String id = JSONUtil.parseObj(recordsArray.get(i)).getStr("id");
                String deleteUrl = "https://api.cloudflare.com/client/v4/zones/" + params.getZoneId() + "/dns_records/" + id;
                HttpRequest deleteRequest = HttpRequest.delete(deleteUrl)
                        .header("Authorization", "Bearer " + params.getApiToken())
                        .header("Content-Type", "application/json");
                HttpResponse deleteResponse = deleteRequest.execute();
                if (deleteResponse.getStatus() != 200) {
                    log.error("Error executing delete command");
                    throw new OciException(-1, "清理域名：" + proxyDomain + " 的 DNS 记录失败");
                }
            }
            log.info("√√√ 域名：" + proxyDomain + " 的dns记录已清除！ √√√");
        });
    }

    @Override
    public void removeCfDnsByIdsRecords(RemoveCfDnsByIdsParams params) {
        params.getRecordIds().parallelStream().forEach(id -> {
            String deleteUrl = "https://api.cloudflare.com/client/v4/zones/" + params.getZoneId() + "/dns_records/" + id;
            HttpRequest deleteRequest = HttpRequest.delete(deleteUrl)
                    .header("Authorization", "Bearer " + params.getApiToken())
                    .header("Content-Type", "application/json");
            HttpResponse deleteResponse = deleteRequest.execute();
            if (deleteResponse.getStatus() != 200) {
                log.error("Error executing delete command");
                throw new OciException(-1, "删除 DNS 记录失败，接口返回数据：" + deleteResponse.body());
            }
        });
    }

    @Override
    public HttpResponse updateCfDnsRecords(UpdateCfDnsRecordsParams params) {
        String url = String.format("https://api.cloudflare.com/client/v4/zones/%s/dns_records/%s", params.getZoneId(), params.getId());
        JSONObject data = new JSONObject();
        data.set("type", params.getType());
        data.set("name", params.getName());
        data.set("content", params.getIpAddress());
        data.set("proxied", params.isProxied());
        data.set("ttl", params.getTtl());
        data.set("comment", params.getComment());
        return HttpRequest.patch(url)
                .header("Authorization", "Bearer " + params.getApiToken())
                .header("Content-Type", "application/json")
                .body(data.toString())
                .execute();
    }

    @Override
    public List<CfDnsRecordDTO> getCfDnsRecords(GetCfDnsRecordsParams params) {
        String url = "https://api.cloudflare.com/client/v4/zones/" + params.getZoneId() + "/dns_records";
        if (!StrUtil.isBlank(params.getType())) {
            url += "?type=" + params.getType();
            if (!StrUtil.isBlank(params.getDomain())) {
                url += "&name=" + params.getDomain();
            }
        }

        HttpRequest request = HttpRequest.get(url)
                .header("Authorization", "Bearer " + params.getApiToken())
                .header("Content-Type", "application/json");
        HttpResponse response = request.execute();
        String responseBody = response.body();
        if (!Boolean.parseBoolean(String.valueOf(JSONUtil.parseObj(responseBody).get("success")))) {
            log.error("获取：[{}] 的 DNS 记录失败，接口返回数据：{}", params.getDomain(), responseBody);
            return Collections.emptyList();
        }

        JSONArray recordsArray = JSONUtil.parseArray(JSONUtil.parseObj(responseBody).get("result"));
        return recordsArray.stream()
                .filter(Objects::nonNull)
                .map(x -> JSONUtil.toBean(JSONUtil.parseObj(x), CfDnsRecordDTO.class))
                .collect(Collectors.toList());
    }

    public static void main(String[] args) {
        String zoneId = "";
        String apiToken = "";

//        AddCfDnsRecordsParams addCfDnsRecordsParams = new AddCfDnsRecordsParams();
//        addCfDnsRecordsParams.setDomainPrefix("oci-test");
//        addCfDnsRecordsParams.setType("A");
//        addCfDnsRecordsParams.setProxied(true);
//        addCfDnsRecordsParams.setIpAddress("1.2.3.4");
//        addCfDnsRecordsParams.setZoneId(zoneId);
//        addCfDnsRecordsParams.setApiToken(apiToken);
//        System.out.println(new CfApiServiceImpl().addCfDnsRecords(addCfDnsRecordsParams).body());

        UpdateCfDnsRecordsParams updateCfDnsRecordsParams = new UpdateCfDnsRecordsParams();
        updateCfDnsRecordsParams.setZoneId(zoneId);
        updateCfDnsRecordsParams.setApiToken(apiToken);
        updateCfDnsRecordsParams.setId("e97f24004f5c7df7b1133c75293e4973");
        updateCfDnsRecordsParams.setName("oci-test2");
        updateCfDnsRecordsParams.setType("A");
        updateCfDnsRecordsParams.setIpAddress("11.22.33.44");
        updateCfDnsRecordsParams.setProxied(true);
        System.out.println(new CfApiServiceImpl().updateCfDnsRecords(updateCfDnsRecordsParams).body());
    }
}
