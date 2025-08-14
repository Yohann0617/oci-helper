package com.yohann.ocihelper.bean.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * <p>
 * SysUser
 * </p >
 *
 * @author yohann
 * @since 2024/11/7 14:34
 */
@Data
@Builder
public class SysUserDTO {

    private String taskId;
    private OciCfg ociCfg;
    private String username;
    private float ocpus = 1F;
    private float memory = 6F;
    private Long disk;
    private String architecture = "ARM";
    private Long interval = 60L;
    private volatile int createNumbers = 0;
    private String rootPassword;
    private String operationSystem = "Ubuntu";
    private List<CloudInstance> instanceList;

    @Data
    @Builder
    public static class OciCfg {
        private String tenantId;
        private String userId;
        private String fingerprint;
        private String privateKeyPath;
        private String region;
        private String compartmentId;
    }

    @Builder
    @Data
    public static class CloudInstance {
        private String region;
        private String name;
        private String ocId;
        private List<String> publicIp;
        private String shape;
//    private String volumeSize;
    }
}
