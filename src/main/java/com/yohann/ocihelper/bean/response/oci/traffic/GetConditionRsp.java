package com.yohann.ocihelper.bean.response.oci.traffic;

import com.yohann.ocihelper.bean.dto.ValueLabelDTO;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.response.oci.traffic
 * @className: GetConditionRsp
 * @author: Yohann
 * @date: 2025/3/7 21:33
 */
@Data
public class GetConditionRsp {

    private List<ValueLabelDTO> regionOptions;
    private Map<String, List<ValueLabelDTO>> instanceOptions;
}
