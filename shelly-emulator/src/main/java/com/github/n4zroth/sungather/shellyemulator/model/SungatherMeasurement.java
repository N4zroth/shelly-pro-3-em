package com.github.n4zroth.sungather.shellyemulator.model;

import java.time.Instant;

public record SungatherMeasurement(Instant time, Double totalPower) {

}
