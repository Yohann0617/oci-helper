package com.yohann.ocihelper.bean.params.cf;

import com.yohann.ocihelper.bean.params.BasicPageParams;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @ClassName ListCfDnsRecordsParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-21 15:09
 **/
@EqualsAndHashCode(callSuper = true)
@Data
public class ListCfDnsRecordsParams extends BasicPageParams {

    private String cfCfgId;
    private Boolean cleanReLaunch;

}
