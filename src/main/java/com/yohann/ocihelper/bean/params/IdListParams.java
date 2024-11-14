package com.yohann.ocihelper.bean.params;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * <p>
 * IdListParams
 * </p >
 *
 * @author yohann
 * @since 2024/11/13 17:00
 */
@Data
public class IdListParams {

    @NotEmpty(message = "id不能为空")
    private List<String> idList;
}
