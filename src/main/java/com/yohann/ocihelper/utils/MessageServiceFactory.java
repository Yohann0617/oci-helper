package com.yohann.ocihelper.utils;

import com.yohann.ocihelper.enums.MessageTypeEnum;
import com.yohann.ocihelper.service.IMessageService;
import com.yohann.ocihelper.service.impl.DingMessageServiceImpl;
import com.yohann.ocihelper.service.impl.TgMessageServiceImpl;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * <p>
 * MessageServiceFactory
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/8 12:12
 */
@Component
public class MessageServiceFactory {

    @Resource
    private TgMessageServiceImpl tgMessageService;
    @Resource
    private DingMessageServiceImpl dingMessageService;

    public IMessageService getMessageService(MessageTypeEnum type) {
        switch (type) {
            case MSG_TYPE_TELEGRAM:
                return tgMessageService;
            case MSG_TYPE_DING_DING:
                return dingMessageService;
            default:
                throw new IllegalArgumentException("Unknown message service type: " + type.getType());
        }
    }
}
