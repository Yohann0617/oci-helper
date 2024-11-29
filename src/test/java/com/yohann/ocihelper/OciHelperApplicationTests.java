package com.yohann.ocihelper;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yohann.ocihelper.service.IOciUserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.File;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class OciHelperApplicationTests {

    @Resource
    private IOciUserService ociUserService;

    String dateFilePath = "C:\\Users\\Yohann\\Desktop\\data.json";


    @Test
    void contextLoads() {

        // 获取所有带 @Service 注解的 Bean
        Map<String, IService> serviceMap = SpringUtil.getBeanFactory().getBeansOfType(IService.class);
        Map<String, List> listMap = serviceMap.entrySet().parallelStream()
                .collect(Collectors.toMap(Map.Entry::getKey, (x) -> x.getValue().list()));
        System.out.println(listMap);

        System.out.println("---------------------");
        String jsonStr = JSONUtil.toJsonStr(listMap);
        System.out.println(jsonStr);

        System.out.println("---------------------");

        File dataFile = FileUtil.touch(dateFilePath);
        FileUtil.writeString(jsonStr, dataFile, Charset.defaultCharset());


    }

    @Test
    void test1() {
        Map<String, IService> serviceMap = SpringUtil.getBeanFactory().getBeansOfType(IService.class);
        List<String> impls = new ArrayList<>(serviceMap.keySet());

        String readJsonStr = FileUtil.readUtf8String(dateFilePath);
        Map<String, List> map = JSONUtil.toBean(readJsonStr, Map.class);
        System.out.println(map);

        impls.forEach(x -> {
            List list = map.get(x);
            list.forEach(obj -> {
                try {
                    BeanUtil.setFieldValue(obj, "createTime", LocalDateTime.now()); // 设置为 null 或移除
                } catch (Exception ignored) {
                }
            });

            System.out.println(list);
            serviceMap.get(x).saveBatch(list);
        });
    }

    @Test
    void test2() {
        System.out.println(ociUserService.list());
    }

}
