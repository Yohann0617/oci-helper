package com.yohann.ocihelper.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yohann.ocihelper.bean.entity.OciKv;
import com.yohann.ocihelper.bean.params.sys.*;
import com.yohann.ocihelper.bean.response.sys.GetSysCfgRsp;
import com.yohann.ocihelper.enums.MessageTypeEnum;
import com.yohann.ocihelper.enums.SysCfgEnum;
import com.yohann.ocihelper.enums.SysCfgTypeEnum;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.IOciKvService;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.utils.CommonUtils;
import com.yohann.ocihelper.utils.MessageServiceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.service.impl
 * @className: ISysServiceImpl
 * @author: Yohann
 * @date: 2024/11/30 17:09
 */
@Service
public class ISysServiceImpl implements ISysService {

    @Value("${web.account}")
    private String account;
    @Value("${web.password}")
    private String password;

    @Resource
    private MessageServiceFactory messageServiceFactory;
    @Resource
    private IOciKvService kvService;

    @Override
    public void sendMessage(String message) {
        messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_DING_DING).sendMessage(message);
        messageServiceFactory.getMessageService(MessageTypeEnum.MSG_TYPE_TELEGRAM).sendMessage(message);
    }

    @Override
    public String login(LoginParams params) {
        if (!params.getAccount().equals(account) || !params.getPassword().equals(password)) {
            throw new OciException(-1, "账号或密码不正确");
        }
        Map<String, Object> payload = new HashMap<>(1);
        payload.put("account", CommonUtils.getMD5(account));
        return CommonUtils.genToken(payload, password);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSysCfg(UpdateSysCfgParams params) {
        kvService.remove(new LambdaQueryWrapper<OciKv>().eq(OciKv::getType, SysCfgTypeEnum.SYS_INIT_CFG.getCode()));
        kvService.saveBatch(SysCfgEnum.getCodeListByType(SysCfgTypeEnum.SYS_INIT_CFG).parallelStream()
                .map(x -> {
                    OciKv ociKv = new OciKv();
                    ociKv.setId(IdUtil.getSnowflakeNextIdStr());
                    ociKv.setCode(x.getCode());
                    ociKv.setType(SysCfgTypeEnum.SYS_INIT_CFG.getCode());
                    switch (x) {
                        case SYS_TG_BOT_TOKEN:
                            ociKv.setValue(params.getTgBotToken());
                            break;
                        case SYS_TG_CHAT_ID:
                            ociKv.setValue(params.getTgChatId());
                            break;
                        case SYS_DING_BOT_TOKEN:
                            ociKv.setValue(params.getDingToken());
                            break;
                        case SYS_DING_BOT_SECRET:
                            ociKv.setValue(params.getDingSecret());
                            break;
                        default:
                            break;
                    }
                    return ociKv;
                }).collect(Collectors.toList()));
    }

    @Override
    public GetSysCfgRsp getSysCfg() {
        GetSysCfgRsp rsp = new GetSysCfgRsp();
        rsp.setDingToken(getCfgValue(SysCfgEnum.SYS_DING_BOT_TOKEN));
        rsp.setDingSecret(getCfgValue(SysCfgEnum.SYS_DING_BOT_SECRET));
        rsp.setTgChatId(getCfgValue(SysCfgEnum.SYS_TG_CHAT_ID));
        rsp.setTgBotToken(getCfgValue(SysCfgEnum.SYS_TG_BOT_TOKEN));
        return rsp;
    }

    private String getCfgValue(SysCfgEnum sysCfgEnum) {
        OciKv cfg = kvService.getOne(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, sysCfgEnum.getCode()));
        return cfg == null ? null : cfg.getValue();
    }

}
