package com.github.n4zroth.sungather.shellyemulator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SungatherMessage(
        @JsonProperty(value = "daily_battery_discharge_energy", required = true) double dailyBatteryDischargeEnergy,
        @JsonProperty(value = "daily_direct_energy_consumption", required = true) double dailyDirectEnergyConsumption,
        @JsonProperty(value = "daily_import_energy", required = true) double dailyImportEnergy,
        @JsonProperty(value = "load_power_hybrid", required = true) double currentLoad) {

    public long getDailyConsumption() {
        // This is in kWh we need Wh
        return Math.round((dailyBatteryDischargeEnergy + dailyDirectEnergyConsumption + dailyImportEnergy) * 1000);
    }
}
