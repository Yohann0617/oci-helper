package com.yohann.ocihelper.bean.params;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * <p>
 * CreateInstanceParams
 * </p >
 *
 * @author yohann
 * @since 2024/11/13 19:26
 */
@Data
public class CreateInstanceParams {

    @NotBlank(message = "配置id不能为空")
    private String userId;
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
