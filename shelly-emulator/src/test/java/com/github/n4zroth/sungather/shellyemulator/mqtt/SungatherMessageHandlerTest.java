package com.github.n4zroth.sungather.shellyemulator.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.n4zroth.sungather.shellyemulator.config.MqttConfig;
import com.github.n4zroth.sungather.shellyemulator.fixtures.TestDataBuilder;
import com.github.n4zroth.sungather.shellyemulator.logic.MessageTransformer;
import com.github.n4zroth.sungather.shellyemulator.model.SungatherMessage;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SungatherMessageHandlerTest {

    @Mock
    private MessageTransformer messageTransformer;

    @Mock
    private IMqttClient mqttClient;

    @Mock
    private MqttConfig mqttConfig;

    private ObjectMapper objectMapper;

    private SungatherMessageHandler handler;

    @BeforeEach
    void setUp() throws MqttException {
        objectMapper = new ObjectMapper();
        when(mqttConfig.getInboundTopic()).thenReturn("sungather");

        handler = new SungatherMessageHandler(messageTransformer, mqttClient, mqttConfig, objectMapper);
    }

    @Test
    void shouldSubscribeToInboundTopic_OnConstruction() throws MqttException {
        // Then
        verify(mqttClient).subscribe(eq("sungather"), any(SungatherMessageHandler.class));
    }

    @Test
    void shouldDeserializeAndDelegateMessage_WhenValidJsonReceived() throws Exception {
        // Given
        String validJson = TestDataBuilder.createValidSungatherJson();
        MqttMessage mqttMessage = new MqttMessage(validJson.getBytes());

        // When
        handler.messageArrived("sungather", mqttMessage);

        // Then
        ArgumentCaptor<SungatherMessage> messageCaptor = ArgumentCaptor.forClass(SungatherMessage.class);
        verify(messageTransformer).handleMessage(messageCaptor.capture());

        SungatherMessage captured = messageCaptor.getValue();
        assertThat(captured.dailyBatteryDischargeEnergy()).isEqualTo(5.0);
        assertThat(captured.dailyDirectEnergyConsumption()).isEqualTo(3.0);
        assertThat(captured.dailyImportEnergy()).isEqualTo(2.0);
        assertThat(captured.currentLoad()).isEqualTo(500.0);
    }

    @Test
    void shouldHandleInvalidJson_WithoutCrashing() throws Exception {
        // Given
        String invalidJson = TestDataBuilder.createMalformedJson();
        MqttMessage mqttMessage = new MqttMessage(invalidJson.getBytes());

        // When / Then
        assertThatCode(() -> handler.messageArrived("sungather", mqttMessage))
                .doesNotThrowAnyException();

        // Verify transformer was NOT called
        verify(messageTransformer, never()).handleMessage(any());
    }

    @Test
    void shouldHandleJsonWithMissingFields_WithoutCrashing() throws Exception {
        // Given
        String incompleteJson = TestDataBuilder.createInvalidSungatherJson();
        MqttMessage mqttMessage = new MqttMessage(incompleteJson.getBytes());

        // When / Then
        assertThatCode(() -> handler.messageArrived("sungather", mqttMessage))
                .doesNotThrowAnyException();

        // Verify transformer was NOT called (deserialization should fail due to missing required fields)
        verify(messageTransformer, never()).handleMessage(any());
    }

    @Test
    void shouldHandleEmptyPayload_WithoutCrashing() throws Exception {
        // Given
        MqttMessage mqttMessage = new MqttMessage(new byte[0]);

        // When / Then
        assertThatCode(() -> handler.messageArrived("sungather", mqttMessage))
                .doesNotThrowAnyException();

        // Verify transformer was NOT called
        verify(messageTransformer, never()).handleMessage(any());
    }

    @Test
    void shouldParseMessageCorrectly_WhenUnknownFieldsPresent() throws Exception {
        // Given
        String jsonWithExtraFields = """
                {
                    "daily_battery_discharge_energy": 10.0,
                    "daily_direct_energy_consumption": 5.0,
                    "daily_import_energy": 3.0,
                    "load_power_hybrid": 1000.0,
                    "unknown_field_1": "ignored",
                    "unknown_field_2": 12345
                }
                """;
        MqttMessage mqttMessage = new MqttMessage(jsonWithExtraFields.getBytes());

        // When
        handler.messageArrived("sungather", mqttMessage);

        // Then
        ArgumentCaptor<SungatherMessage> messageCaptor = ArgumentCaptor.forClass(SungatherMessage.class);
        verify(messageTransformer).handleMessage(messageCaptor.capture());

        SungatherMessage captured = messageCaptor.getValue();
        assertThat(captured.dailyBatteryDischargeEnergy()).isEqualTo(10.0);
        assertThat(captured.dailyDirectEnergyConsumption()).isEqualTo(5.0);
        assertThat(captured.dailyImportEnergy()).isEqualTo(3.0);
        assertThat(captured.currentLoad()).isEqualTo(1000.0);
    }

    @Test
    void shouldHandleMultipleMessages_InSequence() throws Exception {
        // Given
        String json1 = objectMapper.writeValueAsString(TestDataBuilder.createSungatherMessage(1.0, 1.0, 1.0, 100.0));
        String json2 = objectMapper.writeValueAsString(TestDataBuilder.createSungatherMessage(2.0, 2.0, 2.0, 200.0));
        String json3 = objectMapper.writeValueAsString(TestDataBuilder.createSungatherMessage(3.0, 3.0, 3.0, 300.0));

        MqttMessage msg1 = new MqttMessage(json1.getBytes());
        MqttMessage msg2 = new MqttMessage(json2.getBytes());
        MqttMessage msg3 = new MqttMessage(json3.getBytes());

        // When
        handler.messageArrived("sungather", msg1);
        handler.messageArrived("sungather", msg2);
        handler.messageArrived("sungather", msg3);

        // Then
        verify(messageTransformer, times(3)).handleMessage(any(SungatherMessage.class));
    }

    @Test
    void shouldConvertPayloadToString_AndDeserialize() throws Exception {
        // Given
        String json = "{\"daily_battery_discharge_energy\":7.5,\"daily_direct_energy_consumption\":4.5,\"daily_import_energy\":2.5,\"load_power_hybrid\":850.0}";
        MqttMessage mqttMessage = new MqttMessage(json.getBytes());

        // When
        handler.messageArrived("sungather", mqttMessage);

        // Then
        ArgumentCaptor<SungatherMessage> messageCaptor = ArgumentCaptor.forClass(SungatherMessage.class);
        verify(messageTransformer).handleMessage(messageCaptor.capture());

        SungatherMessage captured = messageCaptor.getValue();
        assertThat(captured.dailyBatteryDischargeEnergy()).isEqualTo(7.5);
        assertThat(captured.dailyDirectEnergyConsumption()).isEqualTo(4.5);
        assertThat(captured.dailyImportEnergy()).isEqualTo(2.5);
        assertThat(captured.currentLoad()).isEqualTo(850.0);
    }
}
