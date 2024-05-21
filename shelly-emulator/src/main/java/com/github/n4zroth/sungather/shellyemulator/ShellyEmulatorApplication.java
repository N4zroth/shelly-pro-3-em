package com.github.n4zroth.sungather.shellyemulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShellyEmulatorApplication {

    public static void main(final String[] args) {
        SpringApplication.run(ShellyEmulatorApplication.class, args);
    }

}
