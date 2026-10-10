package com.learningcompanion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LearningCompanionApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningCompanionApplication.class, args);
    }
}
