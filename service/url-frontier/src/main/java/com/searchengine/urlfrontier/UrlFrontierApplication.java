package com.searchengine.urlfrontier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UrlFrontierApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlFrontierApplication.class, args);
    }
}
