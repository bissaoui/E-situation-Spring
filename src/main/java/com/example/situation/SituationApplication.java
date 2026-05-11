package com.example.situation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SituationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SituationApplication.class, args);
    }
}
