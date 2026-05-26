package com.engine.web;

import com.engine.domain.CancelAck;
import com.engine.domain.Trade;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

public final class EngineEventStore {
    private final int maxEvents;
    private final ConcurrentLinkedDeque<String> events;
    private final AtomicReference<String> latestSnapshot;

    public EngineEventStore(int maxEvents) {
        if (maxEvents <= 0) {
            throw new IllegalArgumentException("maxEvents must be > 0");
        }
        this.maxEvents = maxEvents;
        this.events = new ConcurrentLinkedDeque<>();
        this.latestSnapshot = new AtomicReference<>("No market data yet.");
    }

    public void onTrade(Trade trade) {
        appendEvent(String.format(
                "[%s] TRADE resting=%s incoming=%s price=%d qty=%d",
                Instant.now(), trade.restingOrderId(), trade.incomingOrderId(), trade.price(), trade.quantity()
        ));
    }

    public void onCancel(CancelAck cancelAck) {
        appendEvent(String.format(
                "[%s] CANCEL order=%s success=%s reason=%s",
                Instant.now(), cancelAck.orderId(), cancelAck.success(), cancelAck.reason()
        ));
    }

    public void onLog(String line) {
        appendEvent(String.format("[%s] ENGINE %s", Instant.now(), line));
    }

    public void onMarketData(String snapshot) {
        latestSnapshot.set(snapshot);
    }

    public List<String> recentEvents(int limit) {
        int safeLimit = Math.max(1, limit);
        List<String> out = new ArrayList<>(Math.min(safeLimit, events.size()));
        int count = 0;
        for (String event : events) {
            if (count++ >= safeLimit) {
                break;
            }
            out.add(event);
        }
        return out;
    }

    public String latestSnapshot() {
        return latestSnapshot.get();
    }

    private void appendEvent(String event) {
        events.addFirst(event);
        while (events.size() > maxEvents) {
            events.pollLast();
        }
    }
}
