package com.github.n4zroth.sungather.shellyemulator.logic;

import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Scope("singleton")
@RequiredArgsConstructor
@Slf4j
public class WbecUpdater {

    private final MeasurementStorage measurementStorage;

    private final WbecClient wbecClient;

    private double lastAvailablePower = Double.MAX_VALUE;

    private double lastBattery = Double.MAX_VALUE;

    @Scheduled(fixedDelay = 5000)
    public void updateWbec() {
        final double batteryPower = measurementStorage.getBatteryPower();
        final double exportPower = measurementStorage.getExportPower();
        final double availablePower;

        // If the battery is currently discharging, 'subtract' (battery power is neg, so actually add)
        // the battery power from the available power, otherwise keep
        if (batteryPower < 0) {
            availablePower = (exportPower + batteryPower) * -1;
        } else {
            availablePower = exportPower * -1;
        }

        if (lastAvailablePower != availablePower) {
            lastAvailablePower = availablePower;
            log.info("Updating WBEC, setting available power to {}", availablePower);
        }
        wbecClient.updatePowerExport(availablePower);

        if (lastBattery != batteryPower) {
            lastBattery = batteryPower;
            log.info("Updating WBEC, setting battery power to {}", batteryPower);
        }
        wbecClient.updateBatteryPower(batteryPower);
    }

}
