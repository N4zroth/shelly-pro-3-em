package com.github.n4zroth.sungather.shellyemulator.logic;

import com.github.n4zroth.sungather.shellyemulator.model.SungatherMessage;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Scope("singleton")
@RequiredArgsConstructor
@Slf4j
public class MeasurementStorage implements SungatherMessageListener {

    @Getter
    private double exportPower;

    @Getter
    private double batteryPower;

    @Override
    public void handleMessage(final SungatherMessage sungatherMessage) {
        exportPower = sungatherMessage.exportPower();
        log.debug("Updated export power to {}", exportPower);

        if (sungatherMessage.batteryState() == 0) {
            batteryPower = sungatherMessage.batteryPower();
        } else {
            batteryPower = sungatherMessage.batteryPower() * -1;
        }
        log.debug("Updated battery power to {}", batteryPower);
    }
}
