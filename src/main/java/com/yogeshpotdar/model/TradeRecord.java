package com.yogeshpotdar.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeRecord {

    @JsonAlias("sym")
    private String symbol;
    @JsonAlias("P")
    private double tradePrice;
    @JsonAlias("Q")
    private double quantity;
    @JsonAlias("TS2")
    private long timestamp;

}
