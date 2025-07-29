package com.yohann.ocihelper;

import com.yohann.ocihelper.utils.DelegatingVirtualTaskScheduler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@MapperScan("com.yohann.ocihelper.mapper")
public class OciHelperApplication {

    public static void main(String[] args) {
        SpringApplication.run(OciHelperApplication.class, args);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2); // 平台线程数（仅调度）
        scheduler.setThreadNamePrefix("spring-scheduler-");
        scheduler.initialize();
        return new DelegatingVirtualTaskScheduler(scheduler);
    }

}
