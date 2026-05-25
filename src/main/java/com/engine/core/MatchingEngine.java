package com.engine.core;

import com.engine.domain.BookLevel;
import com.engine.domain.CancelAck;
import com.engine.domain.Order;
import com.engine.domain.OrderRequest;
import com.engine.domain.OrderType;
import com.engine.domain.Side;
import com.engine.domain.Trade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class MatchingEngine {
    private static final int LATENCY_WINDOW_SIZE = 10_000;

    private final ConcurrentLinkedQueue<OrderRequest> inboundQueue;
    private final OrderBookSide bids;
    private final OrderBookSide asks;
    private final Map<String, Order> orderIndex;
    private final Consumer<Trade> tradeConsumer;
    private final Consumer<CancelAck> cancelConsumer;
    private final Consumer<String> logConsumer;
    private final Consumer<String> marketDataConsumer;
    private final AtomicBoolean running;

    private final long[] latencyWindow;
    private int latencyIndex;
    private int latencyCount;
    private long processedOrders;
    private final long summaryEvery;

    public MatchingEngine(
            ConcurrentLinkedQueue<OrderRequest> inboundQueue,
            Consumer<Trade> tradeConsumer,
            Consumer<CancelAck> cancelConsumer
    ) {
        this(inboundQueue, tradeConsumer, cancelConsumer, msg -> {}, msg -> {}, 50_000L);
    }

    public MatchingEngine(
            ConcurrentLinkedQueue<OrderRequest> inboundQueue,
            Consumer<Trade> tradeConsumer,
            Consumer<CancelAck> cancelConsumer,
            Consumer<String> logConsumer,
            Consumer<String> marketDataConsumer,
            long summaryEvery
    ) {
        this.inboundQueue = Objects.requireNonNull(inboundQueue, "inboundQueue must not be null");
        this.tradeConsumer = Objects.requireNonNull(tradeConsumer, "tradeConsumer must not be null");
        this.cancelConsumer = Objects.requireNonNull(cancelConsumer, "cancelConsumer must not be null");
        this.logConsumer = Objects.requireNonNull(logConsumer, "logConsumer must not be null");
        this.marketDataConsumer = Objects.requireNonNull(marketDataConsumer, "marketDataConsumer must not be null");
        if (summaryEvery <= 0) {
            throw new IllegalArgumentException("summaryEvery must be > 0");
        }
        this.summaryEvery = summaryEvery;

        this.bids = new OrderBookSide(true);
        this.asks = new OrderBookSide(false);
        this.orderIndex = new HashMap<>();
        this.running = new AtomicBoolean(false);

        this.latencyWindow = new long[LATENCY_WINDOW_SIZE];
    }

    public void runLoop() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Engine loop is already running");
        }

        while (running.get()) {
            OrderRequest request = inboundQueue.poll();
            if (request == null) {
                Thread.onSpinWait();
                continue;
            }

            long startNs = System.nanoTime();
            process(request);
            long elapsedNs = System.nanoTime() - startNs;

            recordLatency(elapsedNs);
            processedOrders++;
            if (processedOrders % summaryEvery == 0) {
                logConsumer.accept(buildLatencySummary());
            }
        }
    }

    public void stop() {
        running.set(false);
    }

    public List<Trade> process(OrderRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        if (request.type() == OrderType.CANCEL) {
            handleCancel(request);
            return List.of();
        }

        Order incoming = Order.fromRequest(request);
        List<Trade> trades = new ArrayList<>();

        if (incoming.side() == Side.BUY) {
            match(incoming, asks, trades);
            if (!incoming.isFilled() && incoming.type() == OrderType.LIMIT) {
                bids.add(incoming);
                orderIndex.put(incoming.orderId(), incoming);
            }
        } else {
            match(incoming, bids, trades);
            if (!incoming.isFilled() && incoming.type() == OrderType.LIMIT) {
                asks.add(incoming);
                orderIndex.put(incoming.orderId(), incoming);
            }
        }

        if (!incoming.isFilled() && incoming.type() == OrderType.MARKET) {
            cancelConsumer.accept(new CancelAck(incoming.orderId(), "UNFILLED_MARKET_REMAINDER_CANCELLED", System.nanoTime(), true));
        }

        for (Trade trade : trades) {
            tradeConsumer.accept(trade);
        }
        if (!trades.isEmpty()) {
            marketDataConsumer.accept(formatBookSnapshot(5));
        }

        return trades;
    }

    private void handleCancel(OrderRequest request) {
        Order existing = orderIndex.remove(request.orderId());
        if (existing == null) {
            cancelConsumer.accept(new CancelAck(request.orderId(), "ORDER_NOT_FOUND", System.nanoTime(), false));
            return;
        }

        boolean removed = existing.side() == Side.BUY ? bids.remove(existing) : asks.remove(existing);
        cancelConsumer.accept(new CancelAck(request.orderId(), removed ? "CANCELLED" : "ORDER_NOT_FOUND_IN_BOOK", System.nanoTime(), removed));
    }

    private void match(Order incoming, OrderBookSide oppositeSide, List<Trade> trades) {
        while (!incoming.isFilled()) {
            MatchCandidate candidate = findCandidate(incoming, oppositeSide);
            if (candidate == null) {
                break;
            }

            Order resting = candidate.restingOrder;
            long fillQty = Math.min(incoming.remainingQuantity(), resting.remainingQuantity());
            resting.fill(fillQty);
            incoming.fill(fillQty);

            trades.add(new Trade(
                    resting.orderId(),
                    incoming.orderId(),
                    candidate.price,
                    fillQty,
                    Math.max(incoming.timestampNanos(), resting.timestampNanos())
            ));

            if (resting.isFilled()) {
                candidate.iterator.remove();
                oppositeSide.removePriceLevelIfEmpty(candidate.price);
                orderIndex.remove(resting.orderId());
            }
        }
    }

    private MatchCandidate findCandidate(Order incoming, OrderBookSide oppositeSide) {
        for (Map.Entry<Long, ArrayDeque<Order>> level : oppositeSide.view().entrySet()) {
            long price = level.getKey();
            if (!crosses(incoming, price)) {
                break;
            }

            Iterator<Order> iterator = level.getValue().iterator();
            while (iterator.hasNext()) {
                Order resting = iterator.next();
                if (incoming.userId().equals(resting.userId())) {
                    continue;
                }
                return new MatchCandidate(price, resting, iterator);
            }
        }
        return null;
    }

    private boolean crosses(Order incoming, long restingPrice) {
        if (incoming.type() == OrderType.MARKET) {
            return true;
        }
        return incoming.side() == Side.BUY ? incoming.price() >= restingPrice : incoming.price() <= restingPrice;
    }

    private void recordLatency(long elapsedNs) {
        latencyWindow[latencyIndex] = elapsedNs;
        latencyIndex = (latencyIndex + 1) % LATENCY_WINDOW_SIZE;
        if (latencyCount < LATENCY_WINDOW_SIZE) {
            latencyCount++;
        }
    }

    private String buildLatencySummary() {
        if (latencyCount == 0) {
            return "Matched 0 orders | p50: 0ns | p99: 0ns | max: 0ns";
        }

        long[] sample = Arrays.copyOf(latencyWindow, latencyCount);
        Arrays.sort(sample);
        long p50 = sample[(int) Math.floor((latencyCount - 1) * 0.50)];
        long p99 = sample[(int) Math.floor((latencyCount - 1) * 0.99)];
        long max = sample[latencyCount - 1];

        return String.format("Matched %,d orders | p50: %dns | p99: %dns | max: %dns", processedOrders, p50, p99, max);
    }

    public String formatBookSnapshot(int depth) {
        List<BookLevel> askLevels = asks.getTopLevels(depth);
        List<BookLevel> bidLevels = bids.getTopLevels(depth);

        StringBuilder sb = new StringBuilder();
        sb.append("---- ORDER BOOK ----\n");
        for (BookLevel level : askLevels) {
            sb.append(String.format("ASK %d | %d (%d orders)%n", level.price(), level.totalQuantity(), level.orderCount()));
        }

        Long bestAsk = asks.bestPrice();
        Long bestBid = bids.bestPrice();
        if (bestAsk != null && bestBid != null) {
            sb.append(String.format("---- SPREAD: %d ticks ----%n", bestAsk - bestBid));
        } else {
            sb.append("---- SPREAD: N/A ----\n");
        }

        for (BookLevel level : bidLevels) {
            sb.append(String.format("BID %d | %d (%d orders)%n", level.price(), level.totalQuantity(), level.orderCount()));
        }
        return sb.toString();
    }

    public OrderBookSide bids() {
        return bids;
    }

    public OrderBookSide asks() {
        return asks;
    }

    private record MatchCandidate(long price, Order restingOrder, Iterator<Order> iterator) {
    }
}
