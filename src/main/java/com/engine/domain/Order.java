package com.engine.domain;

import java.util.Objects;

public final class Order {
    private final String orderId;
    private final String userId;
    private final OrderType type;
    private final Side side;
    private final long price;
    private final long originalQuantity;
    private long remainingQuantity;
    private final long timestampNanos;

    public Order(String orderId, String userId, OrderType type, Side side, long price, long quantity, long timestampNanos) {
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null").trim();
        if (this.orderId.isEmpty()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }

        this.userId = Objects.requireNonNull(userId, "userId must not be null").trim();
        if (this.userId.isEmpty()) {
            throw new IllegalArgumentException("userId must not be blank");
        }

        this.type = Objects.requireNonNull(type, "type must not be null");
        this.side = Objects.requireNonNull(side, "side must not be null");

        if (price < 0) {
            throw new IllegalArgumentException("price must be >= 0");
        }
        this.price = price;

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        this.originalQuantity = quantity;
        this.remainingQuantity = quantity;

        if (timestampNanos < 0) {
            throw new IllegalArgumentException("timestampNanos must be >= 0");
        }
        this.timestampNanos = timestampNanos;
    }

    public static Order fromRequest(OrderRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return new Order(
                request.orderId(),
                request.userId(),
                request.type(),
                request.side(),
                request.price(),
                request.quantity(),
                request.timestampNanos()
        );
    }

    public String orderId() { return orderId; }
    public String userId() { return userId; }
    public OrderType type() { return type; }
    public Side side() { return side; }
    public long price() { return price; }
    public long originalQuantity() { return originalQuantity; }
    public long remainingQuantity() { return remainingQuantity; }
    public long timestampNanos() { return timestampNanos; }
    public boolean isFilled() { return remainingQuantity == 0L; }

    public void fill(long executedQuantity) {
        if (executedQuantity <= 0) {
            throw new IllegalArgumentException("executedQuantity must be > 0");
        }
        if (executedQuantity > remainingQuantity) {
            throw new IllegalArgumentException("executedQuantity exceeds remainingQuantity");
        }
        remainingQuantity -= executedQuantity;
    }
}
