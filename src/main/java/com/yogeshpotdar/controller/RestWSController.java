package com.yogeshpotdar.controller;

import com.yogeshpotdar.model.ChartDataKey;
import com.yogeshpotdar.model.websocket.Event;
import com.yogeshpotdar.model.websocket.EventResponse;
import com.yogeshpotdar.process.OHLCAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("trade")
public class RestWSController {

    @PostMapping("/event")
    public @ResponseBody EventResponse event(@RequestBody Event message) throws InterruptedException {
        log.info("invoked events with message={}",message);

        ChartDataKey chartDataKey = new ChartDataKey(message.getSymbol(), Instant.ofEpochMilli(1538409725339216500L), 1);
        OHLCAnalyzer.addSubscription(chartDataKey);
        OHLCAnalyzer.process();
        return new EventResponse(message.getEvent(),message.getSymbol());
    }

}