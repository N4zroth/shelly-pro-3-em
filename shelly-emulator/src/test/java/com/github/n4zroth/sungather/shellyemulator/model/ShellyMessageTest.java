package com.github.n4zroth.sungather.shellyemulator.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShellyMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeShellyMessageCurrent_ToCorrectJsonFormat() throws Exception {
        // Given
        ShellyMessageCurrent message = new ShellyMessageCurrent(750.5);

        // When
        String json = objectMapper.writeValueAsString(message);

        // Then
        assertThat(json).contains("\"total_act_power\"");
        assertThat(json).contains("750.5");
    }

    @Test
    void shouldDeserializeShellyMessageCurrent_FromJson() throws Exception {
        // Given
        String json = "{\"total_act_power\":750.5}";

        // When
        ShellyMessageCurrent message = objectMapper.readValue(json, ShellyMessageCurrent.class);

        // Then
        assertThat(message.currentLoad()).isEqualTo(750.5);
    }

    @Test
    void shouldSerializeShellyMessageTotal_ToCorrectJsonFormat() throws Exception {
        // Given
        ShellyMessageTotal message = new ShellyMessageTotal(10000.0);

        // When
        String json = objectMapper.writeValueAsString(message);

        // Then
        assertThat(json).contains("\"total_act\"");
        assertThat(json).contains("10000.0");
    }

    @Test
    void shouldDeserializeShellyMessageTotal_FromJson() throws Exception {
        // Given
        String json = "{\"total_act\":10000.0}";

        // When
        ShellyMessageTotal message = objectMapper.readValue(json, ShellyMessageTotal.class);

        // Then
        assertThat(message.totalPower()).isEqualTo(10000.0);
    }

    @Test
    void shouldHandleZeroValues_InShellyMessageCurrent() throws Exception {
        // Given
        ShellyMessageCurrent message = new ShellyMessageCurrent(0.0);

        // When
        String json = objectMapper.writeValueAsString(message);
        ShellyMessageCurrent deserialized = objectMapper.readValue(json, ShellyMessageCurrent.class);

        // Then
        assertThat(deserialized.currentLoad()).isEqualTo(0.0);
    }

    @Test
    void shouldHandleZeroValues_InShellyMessageTotal() throws Exception {
        // Given
        ShellyMessageTotal message = new ShellyMessageTotal(0.0);

        // When
        String json = objectMapper.writeValueAsString(message);
        ShellyMessageTotal deserialized = objectMapper.readValue(json, ShellyMessageTotal.class);

        // Then
        assertThat(deserialized.totalPower()).isEqualTo(0.0);
    }

    @Test
    void shouldHandleLargeValues_InShellyMessages() throws Exception {
        // Given
        ShellyMessageCurrent currentMessage = new ShellyMessageCurrent(99999.99);
        ShellyMessageTotal totalMessage = new ShellyMessageTotal(999999999.99);

        // When
        String currentJson = objectMapper.writeValueAsString(currentMessage);
        String totalJson = objectMapper.writeValueAsString(totalMessage);

        // Then - large numbers may be in scientific notation
        assertThat(currentJson).contains("99999.99");
        assertThat(totalJson).matches(".*\"total_act\"\\s*:\\s*9\\.9999999999[Ee]8.*");
    }
}
