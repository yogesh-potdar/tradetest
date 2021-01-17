package com.yogeshpotdar.model.websocket;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
public class EventResponse implements Serializable {
    private String event;
    private String symbol;
}
