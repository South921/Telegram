package com.telegram;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@SpringBootApplication
@ServletComponentScan
@MapperScan(basePackages = {"com.telegram.dao"})
public class TelegramApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelegramApplication.class, args);
    }

}
