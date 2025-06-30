package com.yohann.ocihelper.bean.params.cf;

import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * @ClassName OciRemoveCfDnsRecordsParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-21 14:53
 **/
@Data
public class OciRemoveCfDnsRecordsParams {

    private String cfCfgId;
    @NotEmpty(message = "记录ID不能为空")
    private List<String> recordIds;
}
