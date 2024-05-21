package com.github.n4zroth.sungather.shellyemulator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@ConfigurationProperties(prefix = "mqtt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MqttConfig {

    private String host;

    private int port;

    private String inboundTopic;

    private String outboundCurrentTopic;

    private String outboundTotalTopic;

}
