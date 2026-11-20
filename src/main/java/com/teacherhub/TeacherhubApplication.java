package com.teacherhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TeacherhubApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeacherhubApplication.class, args);
    }

}
