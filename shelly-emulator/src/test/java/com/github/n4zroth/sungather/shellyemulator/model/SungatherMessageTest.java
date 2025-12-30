package com.github.n4zroth.sungather.shellyemulator.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SungatherMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCalculateDailyConsumption_WithValidValues() {
        // Given
        SungatherMessage message = new SungatherMessage(5.0, 3.0, 2.0, 500.0, 0.0, 0, 0.0);

        // When
        long result = message.getDailyConsumption();

        // Then
        // (5.0 + 3.0 + 2.0) * 1000 = 10000 Wh
        assertThat(result).isEqualTo(10000L);
    }

    @Test
    void shouldCalculateDailyConsumption_WithZeroValues() {
        // Given
        SungatherMessage message = new SungatherMessage(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0.0);

        // When
        long result = message.getDailyConsumption();

        // Then
        assertThat(result).isEqualTo(0L);
    }

    @Test
    void shouldCalculateDailyConsumption_WithFractionalValues() {
        // Given
        SungatherMessage message = new SungatherMessage(1.5, 2.3, 3.7, 100.0, 0.0, 0, 0.0);

        // When
        long result = message.getDailyConsumption();

        // Then
        // (1.5 + 2.3 + 3.7) * 1000 = 7500 Wh (rounded)
        assertThat(result).isEqualTo(7500L);
    }

    @Test
    void shouldCalculateDailyConsumption_WithLargeValues() {
        // Given
        SungatherMessage message = new SungatherMessage(100.0, 75.5, 50.25, 10000.0, 0.0, 0, 0.0);

        // When
        long result = message.getDailyConsumption();

        // Then
        // (100.0 + 75.5 + 50.25) * 1000 = 225750 Wh
        assertThat(result).isEqualTo(225750L);
    }

    @Test
    void shouldRoundDailyConsumption_Correctly() {
        // Given - values that result in fractional Wh
        SungatherMessage message = new SungatherMessage(1.1111, 2.2222, 3.3333, 100.0, 0.0, 0, 0.0);

        // When
        long result = message.getDailyConsumption();

        // Then
        // (1.1111 + 2.2222 + 3.3333) * 1000 = 6666.6 → 6667 (rounded)
        assertThat(result).isEqualTo(6667L);
    }

    @Test
    void shouldDeserializeFromJson_WithValidData() throws Exception {
        // Given
        String json = """
                {
                    "daily_battery_discharge_energy": 5.0,
                    "daily_direct_energy_consumption": 3.0,
                    "daily_import_energy": 2.0,
                    "load_power_hybrid": 500.0,
                    "export_power_hybrid": 0.0,
                    "state_battery_discharging": 0,
                    "battery_power": 0.0
                }
                """;

        // When
        SungatherMessage message = objectMapper.readValue(json, SungatherMessage.class);

        // Then
        assertThat(message.dailyBatteryDischargeEnergy()).isEqualTo(5.0);
        assertThat(message.dailyDirectEnergyConsumption()).isEqualTo(3.0);
        assertThat(message.dailyImportEnergy()).isEqualTo(2.0);
        assertThat(message.currentLoad()).isEqualTo(500.0);
        assertThat(message.getDailyConsumption()).isEqualTo(10000L);
    }

    @Test
    void shouldDeserializeFromJson_IgnoringUnknownFields() throws Exception {
        // Given
        String json = """
                {
                    "daily_battery_discharge_energy": 5.0,
                    "daily_direct_energy_consumption": 3.0,
                    "daily_import_energy": 2.0,
                    "load_power_hybrid": 500.0,
                    "export_power_hybrid": 0.0,
                    "state_battery_discharging": 0,
                    "battery_power": 0.0,
                    "unknown_field": "should be ignored"
                }
                """;

        // When
        SungatherMessage message = objectMapper.readValue(json, SungatherMessage.class);

        // Then
        assertThat(message.dailyBatteryDischargeEnergy()).isEqualTo(5.0);
        assertThat(message.dailyDirectEnergyConsumption()).isEqualTo(3.0);
        assertThat(message.dailyImportEnergy()).isEqualTo(2.0);
        assertThat(message.currentLoad()).isEqualTo(500.0);
    }

    @Test
    void shouldHandleJsonPropertyMapping_Correctly() throws Exception {
        // Given - ensure snake_case JSON properties map to camelCase Java fields
        String json = """
                {
                    "daily_battery_discharge_energy": 1.0,
                    "daily_direct_energy_consumption": 2.0,
                    "daily_import_energy": 3.0,
                    "load_power_hybrid": 400.0,
                    "export_power_hybrid": 0.0,
                    "state_battery_discharging": 0,
                    "battery_power": 0.0
                }
                """;

        // When
        SungatherMessage message = objectMapper.readValue(json, SungatherMessage.class);

        // Then
        assertThat(message).isNotNull();
        assertThat(message.dailyBatteryDischargeEnergy()).isEqualTo(1.0);
        assertThat(message.dailyDirectEnergyConsumption()).isEqualTo(2.0);
        assertThat(message.dailyImportEnergy()).isEqualTo(3.0);
        assertThat(message.currentLoad()).isEqualTo(400.0);
    }
}
