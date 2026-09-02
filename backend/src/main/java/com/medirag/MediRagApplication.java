package com.medirag;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.medirag.mapper")
@EnableAsync
public class MediRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediRagApplication.class, args);
    }
}
