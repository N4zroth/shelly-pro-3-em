package com.github.n4zroth.sungather.shellyemulator.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.n4zroth.sungather.shellyemulator.config.MqttConfig;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class MqttConfiguration {

    private final MqttConfig mqttConfig;

    @Bean
    public IMqttClient mqttClient() throws MqttException {

        final IMqttClient mqttClient =
                new MqttClient("tcp://" + mqttConfig.getHost() + ":" + mqttConfig.getPort(), "shelly-emulator");

        mqttClient.connect();

        return mqttClient;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
