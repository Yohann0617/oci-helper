package com.yohann.ocihelper.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @projectName: scan-proxyip
 * @package: com.proxyip.select.config
 * @className: MybatisPlusConfig
 * @author: Yohann
 * @date: 2024/3/30 17:27
 */
@Configuration
public class MybatisPlusConfig {

//    @Bean
//    public MybatisPlusInterceptor mybatisPlusInterceptor() {
//        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
//        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.SQLITE));
//        return interceptor;
//    }
}
