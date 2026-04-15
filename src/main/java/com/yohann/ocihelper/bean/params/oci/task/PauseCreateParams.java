package com.yohann.ocihelper.bean.params.oci.task;

import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * <p>
 * PauseCreateParams - batch pause or resume create tasks
 * </p>
 *
 * @author yohann
 */
@Data
public class PauseCreateParams {

    @NotEmpty(message = "id列表不能为空")
    private List<String> idList;
}
