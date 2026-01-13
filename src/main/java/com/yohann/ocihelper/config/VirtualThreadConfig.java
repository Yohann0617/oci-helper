package com.yohann.ocihelper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @ClassName VirtualThreadConfig
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-08-11 14:33
 **/
@Configuration
public class VirtualThreadConfig {

    public final static ExecutorService VIRTUAL_EXECUTOR = Executors.newThreadPerTaskExecutor(Thread.ofVirtual()
            .name("virtual-thread")
            .factory());

    @Bean(destroyMethod = "close")
    public ExecutorService virtualThreadExecutor() {
        return VIRTUAL_EXECUTOR;
    }
}
