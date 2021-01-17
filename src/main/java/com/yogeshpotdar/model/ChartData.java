package com.yogeshpotdar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@ToString
@EqualsAndHashCode (onlyExplicitlyIncluded = true)
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartData {

    @JsonProperty("o")
    private double open;
    @JsonProperty("h")
    private double high;
    @JsonProperty("l")
    private double low;
    @JsonProperty("c")
    private double close;

    @JsonProperty("volume")
    private double volume;

    private String event;

    @EqualsAndHashCode.Include
    private String symbol;

    @JsonProperty("bar_num")
    private long sequence;

    @JsonIgnore
    private double latest;

    public ChartData(String event, String symbol, long sequence) {
        this.event = event;
        this.symbol = symbol;
        this.sequence = sequence;
    }

    public void setClose() {
        this.close = this.latest;
    }

    @JsonIgnore
    public boolean isEmpty()
    {
        return this.open == 0.0;
    }

}
