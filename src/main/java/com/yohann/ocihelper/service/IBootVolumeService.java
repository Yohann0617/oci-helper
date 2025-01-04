package com.yohann.ocihelper.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yohann.ocihelper.bean.params.oci.BootVolumePageParams;
import com.yohann.ocihelper.bean.params.oci.TerminateBootVolumeParams;
import com.yohann.ocihelper.bean.params.oci.UpdateBootVolumeParams;
import com.yohann.ocihelper.bean.response.oci.BootVolumeListPage;

/**
 * <p>
 * IBootVolumeService
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/12/31 15:50
 */
public interface IBootVolumeService {

    Page<BootVolumeListPage.BootVolumeInfo> bootVolumeListPage(BootVolumePageParams params);

    void terminateBootVolume(TerminateBootVolumeParams params);

    void update(UpdateBootVolumeParams params);
}
