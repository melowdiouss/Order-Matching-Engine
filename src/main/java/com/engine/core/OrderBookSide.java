package com.engine.core;

import com.engine.domain.BookLevel;
import com.engine.domain.Order;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public final class OrderBookSide {
    private final TreeMap<Long, ArrayDeque<Order>> priceLevels;

    public OrderBookSide(boolean isBidSide) {
        this.priceLevels = isBidSide ? new TreeMap<>(Collections.reverseOrder()) : new TreeMap<>();
    }

    public void add(Order order) {
        priceLevels.computeIfAbsent(order.price(), ignored -> new ArrayDeque<>()).addLast(order);
    }

    public boolean remove(Order order) {
        ArrayDeque<Order> queue = priceLevels.get(order.price());
        if (queue == null) {
            return false;
        }
        boolean removed = queue.remove(order);
        if (queue.isEmpty()) {
            priceLevels.remove(order.price());
        }
        return removed;
    }

    public Long bestPrice() {
        return priceLevels.isEmpty() ? null : priceLevels.firstKey();
    }

    public void removePriceLevelIfEmpty(long price) {
        ArrayDeque<Order> levelQueue = priceLevels.get(price);
        if (levelQueue != null && levelQueue.isEmpty()) {
            priceLevels.remove(price);
        }
    }

    public List<BookLevel> getTopLevels(int depth) {
        if (depth <= 0) {
            return List.of();
        }
        List<BookLevel> levels = new ArrayList<>(Math.min(depth, priceLevels.size()));
        int count = 0;
        for (var entry : priceLevels.entrySet()) {
            if (count++ >= depth) {
                break;
            }
            long totalQty = 0L;
            for (Order order : entry.getValue()) {
                totalQty += order.remainingQuantity();
            }
            levels.add(new BookLevel(entry.getKey(), totalQty, entry.getValue().size()));
        }
        return levels;
    }

    public NavigableMap<Long, ArrayDeque<Order>> view() {
        return Collections.unmodifiableNavigableMap(priceLevels);
    }

    public boolean isEmpty() {
        return priceLevels.isEmpty();
    }
}
