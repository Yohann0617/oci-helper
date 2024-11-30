package com.yohann.ocihelper.service;

import com.yohann.ocihelper.bean.params.sys.*;
import com.yohann.ocihelper.bean.response.sys.GetSysCfgRsp;

public interface ISysService {

    void sendMessage(String message);

    String login(LoginParams params);

    void updateSysCfg(UpdateSysCfgParams params);

    GetSysCfgRsp getSysCfg();

}
