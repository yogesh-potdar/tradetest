package com.yogeshpotdar.model.websocket;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AllArgsConstructor
public class Event implements Serializable {
    private String event;
    private String symbol;
    private long interval;
}
