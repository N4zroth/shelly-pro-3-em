package com.github.n4zroth.sungather.shellyemulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@ConfigurationProperties(prefix = "influx")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InfluxConfig {

    private String url;

    private String token;

    private String org;

    private String bucket;

}
