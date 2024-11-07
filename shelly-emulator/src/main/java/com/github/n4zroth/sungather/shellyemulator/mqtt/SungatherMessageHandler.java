package com.github.n4zroth.sungather.shellyemulator.mqtt;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.n4zroth.sungather.shellyemulator.config.MqttConfig;
import com.github.n4zroth.sungather.shellyemulator.logic.SungatherMessageListener;
import com.github.n4zroth.sungather.shellyemulator.model.SungatherMessage;

import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SungatherMessageHandler implements IMqttMessageListener {

    private final List<SungatherMessageListener> sungatherMessageListeners;

    private final ObjectMapper objectMapper;

    public SungatherMessageHandler(final List<SungatherMessageListener> sungatherMessageListeners,
            final IMqttClient mqttClient, final MqttConfig mqttConfig, final ObjectMapper objectMapper)
            throws MqttException {
        this.sungatherMessageListeners = sungatherMessageListeners;
        this.objectMapper = objectMapper;

        mqttClient.subscribe(mqttConfig.getInboundTopic(), this);
    }

    @Override
    public void messageArrived(final String topic, final MqttMessage mqttMessage) throws Exception {
        try {
            final String payload = new String(mqttMessage.getPayload());
            log.debug("Received '{}' on topic {}", payload, topic);
            final SungatherMessage sungatherMessage = objectMapper.readValue(payload, SungatherMessage.class);
            sungatherMessageListeners.forEach(s -> s.handleMessage(sungatherMessage));
        } catch (final JsonProcessingException e) {
            log.error("Couldn't parse message", e);
        }
    }
}
