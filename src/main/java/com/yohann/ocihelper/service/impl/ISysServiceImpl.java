package com.yohann.ocihelper.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.service.impl
 * @className: ISysServiceImpl
 * @author: Yohann
 * @date: 2024/11/30 17:09
 */
@Service
@Slf4j
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
        if (getEnableMfa()) {
            if (params.getMfaCode() == null) {
                throw new OciException(-1, "验证码不能为空");
            }
            OciKv mfa = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                    .eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode()));
            if (!CommonUtils.verifyMfaCode(mfa.getValue(), params.getMfaCode())) {
                throw new OciException(-1, "无效的验证码");
            }
        }
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
        if (params.isEnableMfa()) {
            OciKv mfaInDb = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                    .eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode()));
            if (mfaInDb == null) {
                String secretKey = CommonUtils.generateSecretKey();
                OciKv mfa = new OciKv();
                mfa.setId(IdUtil.getSnowflakeNextIdStr());
                mfa.setCode(SysCfgEnum.SYS_MFA_SECRET.getCode());
                mfa.setValue(secretKey);
                mfa.setType(SysCfgTypeEnum.SYS_MFA_CFG.getCode());
                String qrCodeURL = CommonUtils.generateQRCodeURL(secretKey, account, account);
                CommonUtils.genQRPic(CommonUtils.MFA_QR_PNG_PATH, qrCodeURL);
                kvService.save(mfa);
            }
        } else {
            kvService.remove(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode()));
            FileUtil.del(CommonUtils.MFA_QR_PNG_PATH);
        }
    }

    @Override
    public GetSysCfgRsp getSysCfg() {
        GetSysCfgRsp rsp = new GetSysCfgRsp();
        rsp.setDingToken(getCfgValue(SysCfgEnum.SYS_DING_BOT_TOKEN));
        rsp.setDingSecret(getCfgValue(SysCfgEnum.SYS_DING_BOT_SECRET));
        rsp.setTgChatId(getCfgValue(SysCfgEnum.SYS_TG_CHAT_ID));
        rsp.setTgBotToken(getCfgValue(SysCfgEnum.SYS_TG_BOT_TOKEN));
        OciKv mfa = kvService.getOne(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode()));
        rsp.setEnableMfa(mfa != null);
        Optional.ofNullable(mfa).ifPresent(x -> {
            rsp.setMfaSecret(x.getValue());
            try (FileInputStream in = new FileInputStream(CommonUtils.MFA_QR_PNG_PATH);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                IoUtil.copy(in, out);
                rsp.setMfaQrData("data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray()));
            } catch (Exception e) {
                log.error("获取MFA二维码图片失败：{}", e.getLocalizedMessage());
            }
        });
        return rsp;
    }

    @Override
    public boolean getEnableMfa() {
        return kvService.getOne(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_MFA_SECRET.getCode())) != null;
    }

    private String getCfgValue(SysCfgEnum sysCfgEnum) {
        OciKv cfg = kvService.getOne(new LambdaQueryWrapper<OciKv>().eq(OciKv::getCode, sysCfgEnum.getCode()));
        return cfg == null ? null : cfg.getValue();
    }

}
