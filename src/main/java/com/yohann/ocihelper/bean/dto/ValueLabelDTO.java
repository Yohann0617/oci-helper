package com.yohann.ocihelper.bean.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.dto
 * @className: ValueLabelDTO
 * @author: Yohann
 * @date: 2025/3/7 21:16
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ValueLabelDTO {

    private String label;
    private String value;
}
