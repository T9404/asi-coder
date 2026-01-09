package com.example.projectmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ProjectManagerClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProjectManagerClientApplication.class, args);
    }
}
