package com.example.civicgrid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CivicGridApplication {

    public static void main(String[] args) {
        SpringApplication.run(CivicGridApplication.class, args);
    }

}
