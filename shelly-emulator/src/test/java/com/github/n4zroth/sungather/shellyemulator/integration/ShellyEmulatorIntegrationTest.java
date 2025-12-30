package com.github.n4zroth.sungather.shellyemulator.integration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.n4zroth.sungather.shellyemulator.config.IntegrationTestConfiguration;
import com.github.n4zroth.sungather.shellyemulator.fixtures.TestDataBuilder;
import com.github.n4zroth.sungather.shellyemulator.model.ShellyMessageCurrent;
import com.github.n4zroth.sungather.shellyemulator.model.ShellyMessageTotal;
import com.github.n4zroth.sungather.shellyemulator.model.SungatherMessage;
import com.github.n4zroth.sungather.shellyemulator.mqtt.SungatherMessageHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
@Import(IntegrationTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ShellyEmulatorIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldTransformAndPublishMessages_WhenSungatherMessageReceived() throws Exception {
        // Given
        String sungatherJson = TestDataBuilder.createValidSungatherJson();

        // When
        testPublisher.publish(inboundTopic, sungatherJson.getBytes(), 1, false);

        // Then - wait for messages to be processed and published
        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    assertThat(receivedMessages).hasSizeGreaterThanOrEqualTo(2);
                });

        // Separate messages by type
        List<String> currentMessages = receivedMessages.stream()
                .filter(msg -> msg.contains("total_act_power"))
                .toList();
        List<String> totalMessages = receivedMessages.stream()
                .filter(msg -> msg.contains("total_act") && !msg.contains("total_act_power"))
                .toList();

        // Verify current message
        assertThat(currentMessages).isNotEmpty();
        ShellyMessageCurrent current = objectMapper.readValue(currentMessages.get(0), ShellyMessageCurrent.class);
        assertThat(current.currentLoad()).isEqualTo(500.0);

        // Verify total message
        assertThat(totalMessages).isNotEmpty();
        ShellyMessageTotal total = objectMapper.readValue(totalMessages.get(0), ShellyMessageTotal.class);
        assertThat(total.totalPower()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldCalculateCumulativeTotal_AcrossMultipleMessages() throws Exception {
        // Given
        String msg1 = objectMapper.writeValueAsString(
                TestDataBuilder.createSungatherMessage(1.0, 1.0, 1.0, 100.0)); // 3000 Wh
        String msg2 = objectMapper.writeValueAsString(
                TestDataBuilder.createSungatherMessage(2.0, 2.0, 2.0, 200.0)); // 6000 Wh
        String msg3 = objectMapper.writeValueAsString(
                TestDataBuilder.createSungatherMessage(3.0, 3.0, 3.0, 300.0)); // 9000 Wh

        // When
        testPublisher.publish(inboundTopic, msg1.getBytes(), 1, false);
        sleepQuietly(500);

        testPublisher.publish(inboundTopic, msg2.getBytes(), 1, false);
        sleepQuietly(500);

        testPublisher.publish(inboundTopic, msg3.getBytes(), 1, false);

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(receivedMessages).hasSizeGreaterThanOrEqualTo(6));

        // Parse the last total message
        String lastTotalMsg = receivedMessages.stream()
                .filter(msg -> msg.contains("total_act"))
                .reduce((first, second) -> second)
                .orElseThrow();

        ShellyMessageTotal total = objectMapper.readValue(lastTotalMsg, ShellyMessageTotal.class);

        // 0 (initial) + 3000 (msg1) + 3000 (msg2 diff) + 3000 (msg3 diff) = 9000
        assertThat(total.totalPower()).isEqualTo(9000.0);
    }

    @Test
    void shouldDetectDayRollover_InIntegrationScenario() throws Exception {
        // Given
        String lastDayMsg = objectMapper.writeValueAsString(
                TestDataBuilder.createSungatherMessage(20.0, 15.0, 10.0, 500.0)); // 45000 Wh
        String newDayMsg = objectMapper.writeValueAsString(
                TestDataBuilder.createSungatherMessage(1.0, 0.5, 0.5, 400.0));   // 2000 Wh (rollover!)

        // When
        testPublisher.publish(inboundTopic, lastDayMsg.getBytes(), 1, false);
        sleepQuietly(500);

        testPublisher.publish(inboundTopic, newDayMsg.getBytes(), 1, false);

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(receivedMessages).hasSizeGreaterThanOrEqualTo(4));

        // Get last total message
        String lastTotalMsg = receivedMessages.stream()
                .filter(msg -> msg.contains("total_act"))
                .reduce((first, second) -> second)
                .orElseThrow();

        ShellyMessageTotal total = objectMapper.readValue(lastTotalMsg, ShellyMessageTotal.class);

        // 0 + 45000 (last day) + 2000 (new day) = 47000
        assertThat(total.totalPower()).isEqualTo(47000.0);
    }

    @Test
    void shouldPersistToInfluxDB_AndQueryOnRestart() throws Exception {
        // Given - write initial measurement to InfluxDB
        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        influxDBTestHelper.writeMeasurement(yesterday, 5000.0);

        // Wait for InfluxDB to persist
        sleepQuietly(1000);

        // When - send message
        String sungatherJson = objectMapper.writeValueAsString(
                TestDataBuilder.createSungatherMessage(5.0, 3.0, 2.0, 500.0)); // 10000 Wh

        testPublisher.publish(inboundTopic, sungatherJson.getBytes(), 1, false);

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(receivedMessages).hasSizeGreaterThanOrEqualTo(2));

        // Verify total includes previous measurement
        String lastTotalMsg = receivedMessages.stream()
                .filter(msg -> msg.contains("total_act"))
                .reduce((first, second) -> second)
                .orElseThrow();

        ShellyMessageTotal total = objectMapper.readValue(lastTotalMsg, ShellyMessageTotal.class);

        // 5000 (from InfluxDB) + 10000 (new message from previous day) = 15000
        assertThat(total.totalPower()).isEqualTo(15000.0);
    }

    @Test
    void shouldHandleZeroConsumption() throws Exception {
        // Given
        String zeroMsg = objectMapper.writeValueAsString(TestDataBuilder.createZeroSungatherMessage());

        // When
        testPublisher.publish(inboundTopic, zeroMsg.getBytes(), 1, false);

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(receivedMessages).hasSizeGreaterThanOrEqualTo(2));

        assertThat(receivedMessages)
                .anySatisfy(msg -> {
                    ShellyMessageCurrent current = objectMapper.readValue(msg, ShellyMessageCurrent.class);
                    assertThat(current.currentLoad()).isEqualTo(0.0);
                });
    }

    @Test
    void shouldPublishToCorrectTopics() throws Exception {
        // Given
        String sungatherJson = TestDataBuilder.createValidSungatherJson();

        // When
        testPublisher.publish(inboundTopic, sungatherJson.getBytes(), 1, false);

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(receivedMessages).hasSizeGreaterThanOrEqualTo(2));

        // Verify message formats
        assertThat(receivedMessages)
                .anyMatch(msg -> msg.contains("total_act_power"))  // Current message
                .anyMatch(msg -> msg.contains("total_act"));       // Total message
    }

    @Test
    void shouldHandleInvalidJson_Gracefully() throws Exception {
        // Given
        String invalidJson = TestDataBuilder.createMalformedJson();

        // When
        testPublisher.publish(inboundTopic, invalidJson.getBytes(), 1, false);

        // Then - wait a bit and verify no messages were published (error was handled)
        sleepQuietly(2000);

        assertThat(receivedMessages).isEmpty();
    }

    @Test
    void shouldQueryInfluxDB_WhenNoDataExists() throws Exception {
        // Given - no data in InfluxDB
        String sungatherJson = objectMapper.writeValueAsString(
                TestDataBuilder.createSungatherMessage(5.0, 3.0, 2.0, 500.0));

        // When
        testPublisher.publish(inboundTopic, sungatherJson.getBytes(), 1, false);

        // Then
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(receivedMessages).hasSizeGreaterThanOrEqualTo(2));

        // Should initialize with 0 and not fail
        assertThat(receivedMessages).isNotEmpty();
    }
}
