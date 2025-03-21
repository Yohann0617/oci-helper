package com.yohann.ocihelper.bean.response.cf;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @ClassName GetCfCfgSelRsp
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-21 16:20
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetCfCfgSelRsp {

    private List<CfCfgSel> cfCfgSelList;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CfCfgSel{
        private String cfCfgId;
        private String cfgName;
    }
}
