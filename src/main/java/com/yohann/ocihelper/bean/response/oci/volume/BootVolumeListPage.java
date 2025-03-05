package com.yohann.ocihelper.bean.response.oci.volume;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * <p>
 * BootVolumeListRsp
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/12/31 14:24
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class BootVolumeListPage<T> extends Page<T> {

    @Data
    public static class BootVolumeInfo {
        private String id;
        private String availabilityDomain;
        private String displayName;
        private String vpusPerGB;
        private String sizeInGBs;
        private String lifecycleState;
        private String timeCreated;
        private String instanceName;
        private Boolean attached;
        private String jsonStr;
    }

    public static <T> Page<T> buildPage(List<T> entities, long size, long current, long total) {
        BootVolumeListPage<T> page = new BootVolumeListPage<>();
        page.setRecords(entities);
        page.setSize(size);
        page.setCurrent(current);
        page.setTotal(total);
        page.setPages((long) (Math.ceil((double) total / size)));
        return page;
    }
}
