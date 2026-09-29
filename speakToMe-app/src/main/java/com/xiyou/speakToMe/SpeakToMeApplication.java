package com.xiyou.speakToMe;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 西邮点评家 后端启动类。
 * 组件扫描覆盖全部模块包；@MapperScan 注册各模块 mapper（支持通配包路径）。
 */
@SpringBootApplication
@MapperScan("com.xiyou.speakToMe.**.mapper")
public class SpeakToMeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpeakToMeApplication.class, args);
    }
}
