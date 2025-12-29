package com.yohann.ocihelper.telegram.factory;

import com.yohann.ocihelper.telegram.handler.CallbackHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 回调处理器工厂
 * 
 * @author yohann
 */
@Slf4j
@Component
public class CallbackHandlerFactory {
    
    private final List<CallbackHandler> handlers;
    
    @Autowired
    public CallbackHandlerFactory(List<CallbackHandler> handlers) {
        this.handlers = handlers;
        log.info("已加载 {} 个回调处理器", handlers.size());
    }
    
    /**
     * 根据回调数据获取处理器
     * 
     * @param callbackData 回调数据
     * @return 处理器
     */
    public Optional<CallbackHandler> getHandler(String callbackData) {
        return handlers.stream()
                .filter(handler -> handler.canHandle(callbackData))
                .findFirst();
    }
}
