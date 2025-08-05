package com.yohann.ocihelper.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yohann.ocihelper.bean.entity.IpData;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yohann.ocihelper.bean.params.ipdata.AddIpDataParams;
import com.yohann.ocihelper.bean.params.ipdata.PageIpDataParams;
import com.yohann.ocihelper.bean.params.ipdata.RemoveIpDataParams;
import com.yohann.ocihelper.bean.params.ipdata.UpdateIpDataParams;
import com.yohann.ocihelper.bean.response.ipdata.IpDataPageRsp;

/**
* @author Yohann_Fan
* @description 针对表【ip_data】的数据库操作Service
* @createDate 2025-08-04 17:28:41
*/
public interface IIpDataService extends IService<IpData> {

    void add(AddIpDataParams params);

    void loadOciIpData();

    void updateIpData(UpdateIpDataParams params);

    void removeIpData(RemoveIpDataParams params);

    Page<IpDataPageRsp> pageIpData(PageIpDataParams params);
}
