package com.github.n4zroth.sungather.shellyemulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableFeignClients
public class ShellyEmulatorApplication {

    public static void main(final String[] args) {
        SpringApplication.run(ShellyEmulatorApplication.class, args);
    }

}
