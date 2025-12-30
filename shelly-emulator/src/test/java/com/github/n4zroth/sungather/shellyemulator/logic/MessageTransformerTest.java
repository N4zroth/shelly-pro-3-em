package com.github.n4zroth.sungather.shellyemulator.logic;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.n4zroth.sungather.shellyemulator.config.InfluxConfig;
import com.github.n4zroth.sungather.shellyemulator.config.MqttConfig;
import com.github.n4zroth.sungather.shellyemulator.fixtures.TestDataBuilder;
import com.github.n4zroth.sungather.shellyemulator.model.ShellyMessageCurrent;
import com.github.n4zroth.sungather.shellyemulator.model.ShellyMessageTotal;
import com.github.n4zroth.sungather.shellyemulator.model.SungatherMessage;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxTable;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class MessageTransformerTest {

    @Mock
    private InfluxDBClient influxDBClient;

    @Mock
    private QueryApi queryApi;

    @Mock
    private InfluxConfig influxConfig;

    @Mock
    private MqttConfig mqttConfig;

    @Mock
    private IMqttClient mqttClient;

    private MessageTransformer messageTransformer;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(influxDBClient.getQueryApi()).thenReturn(queryApi);
        when(influxConfig.getBucket()).thenReturn("energy");
        when(mqttConfig.getOutboundCurrentTopic()).thenReturn("energy/total/status/em:0");
        when(mqttConfig.getOutboundTotalTopic()).thenReturn("energy/total/status/emdata:0");

        messageTransformer = new MessageTransformer(influxDBClient, influxConfig, mqttConfig, objectMapper, mqttClient);
    }

    @Test
    void shouldInitializeWithEpoch_WhenInfluxDBReturnsNoData() throws Exception {
        // Given
        List<FluxTable> emptyResult = TestDataBuilder.createEmptyFluxTable();
        when(queryApi.query(anyString())).thenReturn(emptyResult);
        SungatherMessage message = TestDataBuilder.createDefaultSungatherMessage();

        // When
        messageTransformer.handleMessage(message);

        // Then
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient, times(2)).publish(anyString(), messageCaptor.capture());

        List<MqttMessage> publishedMessages = messageCaptor.getAllValues();
        String totalJson = new String(publishedMessages.get(1).getPayload());
        ShellyMessageTotal totalMessage = objectMapper.readValue(totalJson, ShellyMessageTotal.class);

        // First run with no data (Instant.EPOCH is before today), adds dailyConsumption to 0
        assertThat(totalMessage.totalPower()).isEqualTo(10000.0);
    }

    @Test
    void shouldNotAddToTotal_WhenApplicationRestartedSameDay() throws Exception {
        // Given - last measurement is from today
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS);
        List<FluxTable> result = TestDataBuilder.createFluxTableWithSingleRecord(today, 5000.0);
        when(queryApi.query(anyString())).thenReturn(result);

        SungatherMessage message = TestDataBuilder.createDefaultSungatherMessage(); // 10000 Wh daily

        // When
        messageTransformer.handleMessage(message);

        // Then
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient, times(2)).publish(anyString(), messageCaptor.capture());

        String totalJson = new String(messageCaptor.getAllValues().get(1).getPayload());
        ShellyMessageTotal totalMessage = objectMapper.readValue(totalJson, ShellyMessageTotal.class);

        // Should stay at 5000.0 because we assume it's a restart (lastMessage is null)
        assertThat(totalMessage.totalPower()).isEqualTo(5000.0);
    }

    @Test
    void shouldAddDailyConsumption_WhenApplicationRestartedPreviousDay() throws Exception {
        // Given - last measurement is from yesterday
        Instant yesterday = Instant.now()
                .truncatedTo(ChronoUnit.DAYS)
                .minus(1, ChronoUnit.DAYS)
                .plus(2, ChronoUnit.HOURS);
        List<FluxTable> result = TestDataBuilder.createFluxTableWithSingleRecord(yesterday, 5000.0);
        when(queryApi.query(anyString())).thenReturn(result);

        SungatherMessage message = TestDataBuilder.createDefaultSungatherMessage(); // 10000 Wh daily

        // When
        messageTransformer.handleMessage(message);

        // Then
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient, times(2)).publish(anyString(), messageCaptor.capture());

        String totalJson = new String(messageCaptor.getAllValues().get(1).getPayload());
        ShellyMessageTotal totalMessage = objectMapper.readValue(totalJson, ShellyMessageTotal.class);

        // Should add daily consumption: 5000 + 10000 = 15000
        assertThat(totalMessage.totalPower()).isEqualTo(15000.0);
    }

    @Test
    void shouldDetectDayRollover_WhenDailyConsumptionDecreases() throws Exception {
        // Given
        List<FluxTable> result = TestDataBuilder.createFluxTableWithSingleRecord(
                Instant.now().minus(1, ChronoUnit.HOURS), 10000.0);
        when(queryApi.query(anyString())).thenReturn(result);

        SungatherMessage firstMessage = TestDataBuilder.createSungatherMessage(20.0, 15.0, 10.0, 500.0); // 45000 Wh
        SungatherMessage secondMessage = TestDataBuilder.createSungatherMessage(1.0, 0.5, 0.5, 400.0);  // 2000 Wh (rollover!)

        // When
        messageTransformer.handleMessage(firstMessage);
        messageTransformer.handleMessage(secondMessage);

        // Then
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient, times(4)).publish(anyString(), messageCaptor.capture());

        String totalJson = new String(messageCaptor.getAllValues().get(3).getPayload());
        ShellyMessageTotal totalMessage = objectMapper.readValue(totalJson, ShellyMessageTotal.class);

        // 10000 (initial, same day restart) + 2000 (new day consumption after detecting rollover) = 12000
        assertThat(totalMessage.totalPower()).isEqualTo(12000.0);
    }

    @Test
    void shouldCalculateIncrementalTotal_WhenNormalMessageReceived() throws Exception {
        // Given
        List<FluxTable> result = TestDataBuilder.createFluxTableWithSingleRecord(
                Instant.now().minus(1, ChronoUnit.HOURS), 10000.0);
        when(queryApi.query(anyString())).thenReturn(result);

        SungatherMessage firstMessage = TestDataBuilder.createSungatherMessage(5.0, 3.0, 2.0, 500.0);  // 10000 Wh
        SungatherMessage secondMessage = TestDataBuilder.createSungatherMessage(6.0, 4.0, 3.0, 600.0); // 13000 Wh

        // When
        messageTransformer.handleMessage(firstMessage);
        messageTransformer.handleMessage(secondMessage);

        // Then
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient, times(4)).publish(anyString(), messageCaptor.capture());

        List<MqttMessage> publishedMessages = messageCaptor.getAllValues();

        // Check second total message
        String totalJson = new String(publishedMessages.get(3).getPayload());
        ShellyMessageTotal totalMessage = objectMapper.readValue(totalJson, ShellyMessageTotal.class);

        // 10000 (initial, same day restart so not updated) + 3000 (difference between msg2 and msg1) = 13000
        assertThat(totalMessage.totalPower()).isEqualTo(13000.0);
    }

    @Test
    void shouldPublishCorrectTopics_WithCorrectQoS() throws Exception {
        // Given
        List<FluxTable> result = TestDataBuilder.createFluxTableWithSingleRecord(
                Instant.now().minus(1, ChronoUnit.HOURS), 1000.0);
        when(queryApi.query(anyString())).thenReturn(result);

        SungatherMessage message = TestDataBuilder.createDefaultSungatherMessage();

        // When
        messageTransformer.handleMessage(message);

        // Then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient, times(2)).publish(topicCaptor.capture(), messageCaptor.capture());

        List<String> topics = topicCaptor.getAllValues();
        List<MqttMessage> messages = messageCaptor.getAllValues();

        // Verify first publish (current topic)
        assertThat(topics.get(0)).isEqualTo("energy/total/status/em:0");
        assertThat(messages.get(0).getQos()).isEqualTo(1);

        // Verify second publish (total topic)
        assertThat(topics.get(1)).isEqualTo("energy/total/status/emdata:0");
        assertThat(messages.get(1).getQos()).isEqualTo(1);
    }

    @Test
    void shouldPublishCurrentLoadMessage_WithCorrectFormat() throws Exception {
        // Given
        List<FluxTable> result = TestDataBuilder.createFluxTableWithSingleRecord(
                Instant.now().minus(1, ChronoUnit.HOURS), 1000.0);
        when(queryApi.query(anyString())).thenReturn(result);

        SungatherMessage message = TestDataBuilder.createSungatherMessage(5.0, 3.0, 2.0, 750.5);

        // When
        messageTransformer.handleMessage(message);

        // Then
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient, times(2)).publish(anyString(), messageCaptor.capture());

        String currentJson = new String(messageCaptor.getAllValues().get(0).getPayload());
        ShellyMessageCurrent currentMessage = objectMapper.readValue(currentJson, ShellyMessageCurrent.class);

        assertThat(currentMessage.currentLoad()).isEqualTo(750.5);
    }

    @Test
    void shouldThrowException_WhenInfluxDBReturnsMultipleTables() throws Exception {
        // Given
        List<FluxTable> result = TestDataBuilder.createMultipleFluxTables();
        when(queryApi.query(anyString())).thenReturn(result);
        SungatherMessage message = TestDataBuilder.createDefaultSungatherMessage();

        // When / Then
        assertThatThrownBy(() -> messageTransformer.handleMessage(message))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class)
                .hasMessageContaining("Received more than one result");
    }

    @Test
    void shouldThrowException_WhenInfluxDBReturnsMultipleRecords() throws Exception {
        // Given
        List<FluxTable> result = TestDataBuilder.createFluxTableWithMultipleRecords();
        when(queryApi.query(anyString())).thenReturn(result);
        SungatherMessage message = TestDataBuilder.createDefaultSungatherMessage();

        // When / Then
        assertThatThrownBy(() -> messageTransformer.handleMessage(message))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class)
                .hasMessageContaining("Received something other than one single record");
    }

    @Test
    void shouldHandleZeroConsumption() throws Exception {
        // Given
        List<FluxTable> result = TestDataBuilder.createFluxTableWithSingleRecord(
                Instant.now().minus(1, ChronoUnit.HOURS), 1000.0);
        when(queryApi.query(anyString())).thenReturn(result);

        SungatherMessage message = TestDataBuilder.createZeroSungatherMessage();

        // When
        messageTransformer.handleMessage(message);

        // Then
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient, times(2)).publish(anyString(), messageCaptor.capture());

        String currentJson = new String(messageCaptor.getAllValues().get(0).getPayload());
        String totalJson = new String(messageCaptor.getAllValues().get(1).getPayload());

        ShellyMessageCurrent currentMessage = objectMapper.readValue(currentJson, ShellyMessageCurrent.class);
        ShellyMessageTotal totalMessage = objectMapper.readValue(totalJson, ShellyMessageTotal.class);

        assertThat(currentMessage.currentLoad()).isEqualTo(0.0);
        assertThat(totalMessage.totalPower()).isEqualTo(1000.0); // Should stay at initial value
    }

    @Test
    void shouldHandleLargeConsumptionValues() throws Exception {
        // Given
        List<FluxTable> result = TestDataBuilder.createFluxTableWithSingleRecord(
                Instant.now().minus(1, ChronoUnit.HOURS), 1000000.0);
        when(queryApi.query(anyString())).thenReturn(result);

        SungatherMessage message = TestDataBuilder.createLargeSungatherMessage(); // 225750 Wh

        // When
        messageTransformer.handleMessage(message);

        // Then
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient, times(2)).publish(anyString(), messageCaptor.capture());

        String totalJson = new String(messageCaptor.getAllValues().get(1).getPayload());
        ShellyMessageTotal totalMessage = objectMapper.readValue(totalJson, ShellyMessageTotal.class);

        // 1000000 (initial, same day restart so not updated)
        assertThat(totalMessage.totalPower()).isEqualTo(1000000.0);
    }

    @Test
    void shouldHandleMultipleMessagesInSequence() throws Exception {
        // Given
        List<FluxTable> result = TestDataBuilder.createFluxTableWithSingleRecord(
                Instant.now().minus(1, ChronoUnit.HOURS), 5000.0);
        when(queryApi.query(anyString())).thenReturn(result);

        SungatherMessage msg1 = TestDataBuilder.createSungatherMessage(1.0, 1.0, 1.0, 100.0); // 3000 Wh
        SungatherMessage msg2 = TestDataBuilder.createSungatherMessage(2.0, 2.0, 2.0, 200.0); // 6000 Wh
        SungatherMessage msg3 = TestDataBuilder.createSungatherMessage(3.0, 3.0, 3.0, 300.0); // 9000 Wh

        // When
        messageTransformer.handleMessage(msg1);
        messageTransformer.handleMessage(msg2);
        messageTransformer.handleMessage(msg3);

        // Then
        ArgumentCaptor<MqttMessage> messageCaptor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mqttClient, times(6)).publish(anyString(), messageCaptor.capture());

        // Check the final total message (6th message, index 5)
        String totalJson = new String(messageCaptor.getAllValues().get(5).getPayload());
        ShellyMessageTotal totalMessage = objectMapper.readValue(totalJson, ShellyMessageTotal.class);

        // 5000 (initial, same day restart) + 3000 (msg2 diff) + 3000 (msg3 diff) = 11000
        assertThat(totalMessage.totalPower()).isEqualTo(11000.0);
    }
}
