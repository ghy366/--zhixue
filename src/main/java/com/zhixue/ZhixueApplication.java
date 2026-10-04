package com.zhixue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZhixueApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZhixueApplication.class, args);
    }
}

//
//* 启动后访问 http://localhost:8080