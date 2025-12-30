package com.github.n4zroth.sungather.shellyemulator.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MqttConfig.class)
@EnableConfigurationProperties(MqttConfig.class)
@TestPropertySource(properties = {
        "mqtt.host=test-host",
        "mqtt.port=1884",
        "mqtt.inbound-topic=test/inbound",
        "mqtt.outbound-current-topic=test/current",
        "mqtt.outbound-total-topic=test/total"
})
class MqttConfigTest {

    @Autowired
    private MqttConfig mqttConfig;

    @Test
    void shouldBindConfigurationProperties_FromApplicationProperties() {
        // Then
        assertThat(mqttConfig.getHost()).isEqualTo("test-host");
        assertThat(mqttConfig.getPort()).isEqualTo(1884);
        assertThat(mqttConfig.getInboundTopic()).isEqualTo("test/inbound");
        assertThat(mqttConfig.getOutboundCurrentTopic()).isEqualTo("test/current");
        assertThat(mqttConfig.getOutboundTotalTopic()).isEqualTo("test/total");
    }

    @Test
    void shouldAllowSettingAndGettingProperties() {
        // Given
        MqttConfig config = new MqttConfig();

        // When
        config.setHost("localhost");
        config.setPort(1883);
        config.setInboundTopic("sungather");
        config.setOutboundCurrentTopic("energy/total/status/em:0");
        config.setOutboundTotalTopic("energy/total/status/emdata:0");

        // Then
        assertThat(config.getHost()).isEqualTo("localhost");
        assertThat(config.getPort()).isEqualTo(1883);
        assertThat(config.getInboundTopic()).isEqualTo("sungather");
        assertThat(config.getOutboundCurrentTopic()).isEqualTo("energy/total/status/em:0");
        assertThat(config.getOutboundTotalTopic()).isEqualTo("energy/total/status/emdata:0");
    }

    @Test
    void shouldSupportAllArgsConstructor() {
        // When
        MqttConfig config = new MqttConfig(
                "broker-host",
                1883,
                "in-topic",
                "out-current",
                "out-total"
        );

        // Then
        assertThat(config.getHost()).isEqualTo("broker-host");
        assertThat(config.getPort()).isEqualTo(1883);
        assertThat(config.getInboundTopic()).isEqualTo("in-topic");
        assertThat(config.getOutboundCurrentTopic()).isEqualTo("out-current");
        assertThat(config.getOutboundTotalTopic()).isEqualTo("out-total");
    }
}
