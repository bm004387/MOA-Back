package com.buc.moa;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.buc.moa.**.mapper")
public class MoaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoaApplication.class, args);
    }
}