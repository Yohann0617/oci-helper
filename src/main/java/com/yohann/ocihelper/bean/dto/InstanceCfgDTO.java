package com.yohann.ocihelper.bean.dto;

import lombok.Builder;
import lombok.Data;

/**
 * <p>
 * InstanceCfgDTO
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/12/18 17:27
 */
@Data
@Builder
public class InstanceCfgDTO {

    private String instanceName;
    private String ipv6;
    private String ocpus;
    private String memory;
    private String bootVolumeSize;
    private String bootVolumeVpu;
}
