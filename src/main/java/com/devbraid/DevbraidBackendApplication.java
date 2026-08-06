package com.devbraid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DevbraidBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevbraidBackendApplication.class, args);
    }

}
