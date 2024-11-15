package com.yohann.ocihelper.bean.params;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.bean.params
 * @className: CreateInstanceBatchParams
 * @author: Yohann
 * @date: 2024/11/16 0:04
 */
@Data
public class CreateInstanceBatchParams {

    @NotEmpty(message = "用户配置id列表不能为空")
    private List<String> userIds;

    private InstanceInfo instanceInfo;

    @Data
    public static class InstanceInfo {
        @NotBlank(message = "CPU不能为空")
        private String ocpus;
        @NotBlank(message = "内存不能为空")
        private String memory;
        @NotBlank(message = "磁盘空间不能为空")
        private String disk;
        @NotBlank(message = "系统架构不能为空")
        private String architecture;
        @NotNull(message = "时间间隔不能为空")
        private Integer interval;
        @NotNull(message = "创建数目不能为空")
        private Integer createNumbers;
        @NotNull(message = "系统类型不能为空")
        private String operationSystem;
        @NotNull(message = "root密码不能为空")
        private String rootPassword;
    }
}
