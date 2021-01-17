package com.yogeshpotdar.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.Beta;
import com.google.common.collect.Lists;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.yogeshpotdar.model.ChartData;
import com.yogeshpotdar.model.ChartDataComparable;
import com.yogeshpotdar.model.ChartDataKey;
import com.yogeshpotdar.model.TradeRecord;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@SuppressWarnings({"unstable"})
public class OHLCAnalyzer {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final String OHLC_NOTIFY = "ohlc_notify";
    private static BlockingQueue<String> inputRecordsQueue = new ArrayBlockingQueue<>(100);

    private static Map<String, List<ChartDataComparable>> symbolToChartDataKeyMap = new HashMap<>();
    private static RangeMap<ChartDataComparable, ChartData> rangeMap = TreeRangeMap.create();

    private static BlockingQueue<ChartData> userPublishQueue = new ArrayBlockingQueue<>(100);
    private static BlockingQueue<ChartDataKey> subscriptionQueue = new ArrayBlockingQueue<>(100);
    private static boolean processingComplete = false;

    private static final Callable<? extends Object> outputWriter = () -> {
        subscriptionQueue.add(new ChartDataKey("XZECXXBT", Instant.ofEpochMilli(1538409725339216500L), 1));
        subscriptionQueue.add(new ChartDataKey("XETHZUSD", Instant.ofEpochMilli(1538409738828589280L), 1));

        boolean processingCompleteFull = false;
        do {
            ChartData chartData = userPublishQueue.take();
            log.trace("chartData={}", chartData);
            String output = OBJECT_MAPPER.writeValueAsString(chartData);
            log.info("{}", output);
//            OutputStream outputStream = Files.newOutputStream(Files.createFile(Paths.get(System.getProperty("user.dir"),"src/main/resources/output/" , chartData.getSymbol()+ "_output.txt")));
//            OBJECT_MAPPER.writeValue(outputStream, chartData);
            if (userPublishQueue.isEmpty()) {
                processingCompleteFull = processingComplete;
            }
        }
        while (!userPublishQueue.isEmpty() && !processingCompleteFull);
        return null;
    };

    private static final Callable<? extends Object> finiteStateMachineWorker = () -> {

        do {
            ChartDataKey subscriptionKey = subscriptionQueue.take();
            log.debug("subscriptionQueue fetched record {}", subscriptionKey);
            if (subscriptionKey.getSequence() == 1) {
                createNewBar(1L, subscriptionKey.getInstant(), subscriptionKey.getSymbol());
                log.debug("initial chart data created");
            }
        }
        while (!subscriptionQueue.isEmpty());

        do {
            String record = inputRecordsQueue.take();
            TradeRecord tradeRecord = OBJECT_MAPPER.readValue(record, TradeRecord.class);
            log.debug("processing tradeRecord={}, for instant={}", tradeRecord, Instant.ofEpochMilli(tradeRecord.getTimestamp()));
            createAdditionalBars(tradeRecord, rangeMap);
            analyseTrade(tradeRecord, rangeMap);
        }
        while (!inputRecordsQueue.isEmpty());
        closeBars(rangeMap);
        processingComplete = true;
        log.debug("rangeMap={}", rangeMap);
        return null;
    };

    private static ChartDataComparable createNewBar(long sequence, Instant startInstant, String symbol) {
        log.debug("createNewBar sequence={},startInstant={},symbol={}", sequence, startInstant, symbol);
        ChartData chartData = new ChartData(OHLC_NOTIFY, symbol, sequence);
        ChartDataComparable chartDataKey1 = new ChartDataKey(symbol, startInstant);
        ChartDataComparable chartDataKey2 = new ChartDataKey(symbol, startInstant.plusSeconds(15));
        Range<ChartDataComparable> range = Range.closedOpen(chartDataKey1, chartDataKey2);
        rangeMap.put(range, chartData);
        List<ChartDataComparable> symbolKeyList = symbolToChartDataKeyMap.getOrDefault(symbol, Lists.newArrayList(chartDataKey1));
        if (!symbolKeyList.contains(chartDataKey1)) {
            return chartDataKey1;
        }
        return null;
    }

    private static final Callable<Void> inputReaderWorker = () -> {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        URI uri = Objects.requireNonNull(contextClassLoader.getResource("json/trades_small.txt")).toURI();
        final Path path = Paths.get(uri);

        try (BufferedReader bufferedReader = Files.newBufferedReader(path, Charset.defaultCharset())) {
            bufferedReader.lines().forEach(inputRecordsQueue::add);
            log.debug("inputRecordsQueue={}", inputRecordsQueue.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    };

    public static void process() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        executorService.submit(inputReaderWorker);
        executorService.submit(finiteStateMachineWorker);
        executorService.submit(outputWriter);

        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);
    }

    private static void closeBars(RangeMap<ChartDataComparable, ChartData> rangeMap) {
        log.debug("closeBars rangeMap={}", rangeMap);
        symbolToChartDataKeyMap.values().forEach(
                (chartDataList) -> {
                    for (ChartDataComparable chartDataComparable : chartDataList) {
                        //No more trade records
                        if (inputRecordsQueue.isEmpty()) {
                            Map.Entry<Range<ChartDataComparable>, ChartData> entry = rangeMap.getEntry(chartDataComparable);
                            entry.getValue().setClose();
                            closeRangeAndPublish(rangeMap, entry, "closeBars - publishing {} and removing from active list");
                        }
                    }
                }
        );
    }

    private static void createAdditionalBars(TradeRecord tradeRecord, RangeMap<ChartDataComparable, ChartData> rangeMap) {
        log.trace("createAdditionalBars tradeRecode={}, rangeMap={}", tradeRecord, rangeMap);
        Instant tradeStartTime = Instant.ofEpochMilli(tradeRecord.getTimestamp());
        ChartDataKey tradeInstantKey = new ChartDataKey(tradeRecord.getSymbol(), tradeStartTime);
        Map.Entry<Range<ChartDataComparable>, ChartData> data = rangeMap.getEntry(tradeInstantKey);

        if (Objects.nonNull(data) && data.getKey().upperEndpoint().compareTo(tradeInstantKey) > 0) {
            log.debug("Bar present for trade={}", tradeInstantKey);
            return;
        }
        //close the old open bars
        do {
            List<ChartDataComparable> chartDataList = symbolToChartDataKeyMap.getOrDefault(tradeRecord.getSymbol(), Collections.emptyList());
            List<Range<ChartDataComparable>> removeList = new ArrayList<>();
            List<ChartDataComparable> addList = new ArrayList<>();
            ChartDataComparable newBarChartKey = null;
            for (ChartDataComparable chartDataComparable : chartDataList) {
                //Time elapsed
                if (chartDataComparable.compareTo(tradeInstantKey) < 0) {
                    log.debug("createAdditionalBars chartDataComparable={},tradeInstantKey={}", chartDataComparable, tradeInstantKey);
                    Map.Entry<Range<ChartDataComparable>, ChartData> entry = rangeMap.getEntry(chartDataComparable);
                    entry.getValue().setClose();
                    Range<ChartDataComparable> rangeToRemove = entry.getKey();
                    Map.Entry<Range<ChartDataComparable>, ChartData> tempEntry = entry;
                    do {
                        newBarChartKey = createNewBar(tempEntry.getValue().getSequence() + 1,
                                ((ChartDataKey) tempEntry.getKey().upperEndpoint()).getInstant(),
                                tradeRecord.getSymbol());

                        tempEntry = rangeMap.getEntry(newBarChartKey);
                        log.debug("New Range created {}", tempEntry.getKey());
                        if (newBarChartKey != null) {
                            if (tempEntry.getKey().contains(tradeInstantKey)) {
                                log.debug("Range containing Trade found {}", tempEntry.getKey());
                                addList.add(newBarChartKey);
                                closeRangeAndPublish(rangeMap, entry, "Closing old Range {} and removing from active list");
                            } else {
                                closeRangeAndPublish(rangeMap, tempEntry, "Range before Trade start so publishing {} and removing from active list");
                            }
                        }
                    } while (!tempEntry.getKey().contains(tradeInstantKey));
                    removeList.add(rangeToRemove);
                }
            }
            removeList.forEach(symbolToChartDataKeyMap::remove);
            symbolToChartDataKeyMap.put(tradeRecord.getSymbol(), addList);
        } while (symbolToChartDataKeyMap.get(tradeRecord.getSymbol()).size() > 1);

    }

    private static void closeRangeAndPublish(RangeMap<ChartDataComparable, ChartData> rangeMap,
                                             Map.Entry<Range<ChartDataComparable>, ChartData> entry, String s) {
        log.debug(s, entry.getValue());
        userPublishQueue.add(entry.getValue());
        rangeMap.remove(entry.getKey());
    }

    private static void analyseTrade(TradeRecord tradeRecord, RangeMap<ChartDataComparable, ChartData> rangeMap) {

        Instant tradeStartTime = Instant.ofEpochMilli(tradeRecord.getTimestamp());
        ChartDataComparable chartDataKey = new ChartDataKey(tradeRecord.getSymbol(), tradeStartTime);
        Map.Entry<Range<ChartDataComparable>, ChartData> data = rangeMap.getEntry(chartDataKey);

        if (Objects.nonNull(data)) {
            ChartData existingData = data.getValue();
            updateChartData(tradeRecord, existingData);
            //For non subscribed trades
            List<ChartDataComparable> symbolKeyList = symbolToChartDataKeyMap.getOrDefault(tradeRecord.getSymbol(),
                    Lists.newArrayList(data.getKey().lowerEndpoint()));
            if (!symbolKeyList.contains(data.getKey().lowerEndpoint())) {
                symbolKeyList.add(data.getKey().lowerEndpoint());
            }
            symbolToChartDataKeyMap.put(tradeRecord.getSymbol(), symbolKeyList);
        }
    }

    private static void updateChartData(TradeRecord tradeRecord, ChartData existingData) {
        double updatedVolume = tradeRecord.getQuantity() + existingData.getVolume();
        double tradePrice = tradeRecord.getTradePrice();
        existingData.setVolume(updatedVolume);
        existingData.setLatest(tradePrice);
        if (existingData.isEmpty()) {
            existingData.setOpen(tradePrice);
            existingData.setHigh(tradePrice);
            existingData.setLow(tradePrice);
        } else if (tradePrice > existingData.getHigh()) {
            existingData.setHigh(tradePrice);
        } else if (tradePrice < existingData.getLow()) {
            existingData.setLow(tradePrice);
        }
    }

    public static void addSubscription(ChartDataKey chartDataKey) {
        subscriptionQueue.add(chartDataKey);
    }
}
