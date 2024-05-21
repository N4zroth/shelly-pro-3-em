package com.github.n4zroth.sungather.shellyemulator.model;

import com.fasterxml.jackson.annotation.JsonProperty;

// em:0
public record ShellyMessageCurrent(@JsonProperty("total_act_power") double currentLoad) {

}
