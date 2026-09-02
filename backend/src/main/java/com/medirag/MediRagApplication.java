package com.medirag;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.medirag.mapper")
public class MediRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediRagApplication.class, args);
    }
}
