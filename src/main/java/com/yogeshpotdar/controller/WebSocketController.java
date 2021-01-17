package com.yogeshpotdar.controller;

import com.yogeshpotdar.model.ChartDataKey;
import com.yogeshpotdar.model.websocket.Event;
import com.yogeshpotdar.model.websocket.EventResponse;
import com.yogeshpotdar.process.OHLCAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.time.Instant;

@Slf4j
@Controller
public class WebSocketController {

    @MessageMapping("/hello")
    @SendTo("/topic/events")
    public EventResponse events(Event message) {
        log.info("invoked events with message={}",message);

        ChartDataKey chartDataKey = new ChartDataKey(message.getSymbol(), Instant.ofEpochMilli(1538409725339216500L), 1);
        OHLCAnalyzer.addSubscription(chartDataKey);

        return new EventResponse(message.getEvent(),message.getSymbol());
    }

}