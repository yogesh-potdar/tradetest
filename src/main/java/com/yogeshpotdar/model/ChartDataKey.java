package com.yogeshpotdar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.time.Instant;
import java.util.Comparator;

@Getter
@Setter
@ToString
@AllArgsConstructor

public class ChartDataKey implements ChartDataComparable {
    @EqualsAndHashCode.Include
    private String symbol;
    @EqualsAndHashCode.Include
    private Instant instant;
    @JsonIgnore
    private long sequence;

    public ChartDataKey(String symbol, Instant instant) {
        this.symbol = symbol;
        this.instant = instant;
    }

    @Override
    public int compareTo(ChartDataKey o) {
        return Comparator.comparing(ChartDataKey::getSymbol)
                .thenComparing(ChartDataKey::getInstant)
                .compare(this,o);
    }
}
