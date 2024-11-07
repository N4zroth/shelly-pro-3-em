package com.github.n4zroth.sungather.shellyemulator.logic;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "wbecClient", url = "${wbec.url}")
public interface WbecClient {
    @GetMapping("pv")
    ResponseEntity<Void> updatePowerExport(@RequestParam("pvWatt") final double powerExport);

    @GetMapping("pv")
    ResponseEntity<Void> updateBatteryPower(@RequestParam("pvBatt") final double batteryPower);
}
