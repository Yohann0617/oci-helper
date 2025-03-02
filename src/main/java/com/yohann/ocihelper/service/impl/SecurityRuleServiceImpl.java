package com.yohann.ocihelper.service.impl;

import com.oracle.bmc.core.model.SecurityList;
import com.oracle.bmc.core.model.Vcn;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.oci.securityrule.GetSecurityRuleListParams;
import com.yohann.ocihelper.bean.response.oci.securityrule.SecurityRuleListRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.ISecurityRuleService;
import com.yohann.ocihelper.service.ISysService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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

    @Override
    public SecurityRuleListRsp getSecurityRuleList(GetSecurityRuleListParams params) {
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        String vcnName = null;
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO);) {
            Vcn vcn = fetcher.getVcnById(params.getVcnId());
            vcnName = vcn.getDisplayName();
            SecurityList securityList = fetcher.listSecurityRule(vcn);
            SecurityRuleListRsp rsp = new SecurityRuleListRsp();
            BeanUtils.copyProperties(securityList, rsp);
            return rsp;
        } catch (Exception e) {
            log.error("用户：[{}]，区域：[{}]，获取vcn：[{}] 的安全列表失败",
                    sysUserDTO.getUsername(), sysUserDTO.getOciCfg().getRegion(), vcnName, e);
            throw new OciException(-1, String.format("获取vcn：[%s] 的安全列表失败", vcnName));
        }
    }
}
