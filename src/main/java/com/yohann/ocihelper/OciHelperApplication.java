package com.yohann.ocihelper;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@MapperScan("com.yohann.ocihelper.mapper")
public class OciHelperApplication {

    public static void main(String[] args) {
        SpringApplication.run(OciHelperApplication.class, args);
    }

}
