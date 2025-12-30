package com.github.n4zroth.sungather.shellyemulator.config;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration for integration tests.
 * Provides separate MQTT clients for subscribing and publishing to avoid conflicts.
 */
@TestConfiguration
public class IntegrationTestConfiguration {

    @Bean
    @Primary
    public IMqttClient mqttClient(MqttConfig mqttConfig) throws MqttException {
        return createMqttClient(mqttConfig, "shelly-emulator-main");
    }

    private IMqttClient createMqttClient(MqttConfig mqttConfig, String clientId) throws MqttException {
        String brokerUrl = "tcp://" + mqttConfig.getHost() + ":" + mqttConfig.getPort();
        IMqttClient client = new MqttClient(brokerUrl, clientId);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);

        // Retry connection to give TestContainer broker time to fully start
        int retries = 20;
        MqttException lastException = null;

        while (retries > 0) {
            try {
                client.connect(options);
                return client;
            } catch (MqttException e) {
                lastException = e;
                retries--;
                if (retries > 0) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new MqttException(ie);
                    }
                }
            }
        }

        throw new MqttException(new RuntimeException("Failed to connect MQTT client after retries", lastException));
    }
}
