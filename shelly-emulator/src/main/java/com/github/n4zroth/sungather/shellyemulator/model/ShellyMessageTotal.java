package com.github.n4zroth.sungather.shellyemulator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// emdata:0
public record ShellyMessageTotal(@JsonProperty("total_act") long totalPower) {

}
