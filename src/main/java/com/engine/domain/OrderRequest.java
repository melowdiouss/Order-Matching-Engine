package com.engine.domain;

import java.util.Objects;

public record OrderRequest(
        String orderId,
        String userId,
        OrderType type,
        Side side,
        long price,
        long quantity,
        long timestampNanos
) {
    public OrderRequest {
        orderId = Objects.requireNonNull(orderId, "orderId must not be null").trim();
        if (orderId.isEmpty()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }

        userId = Objects.requireNonNull(userId, "userId must not be null").trim();
        if (userId.isEmpty()) {
            throw new IllegalArgumentException("userId must not be blank");
        }

        type = Objects.requireNonNull(type, "type must not be null");

        if (timestampNanos < 0) {
            throw new IllegalArgumentException("timestampNanos must be >= 0");
        }

        if (type != OrderType.CANCEL) {
            side = Objects.requireNonNull(side, "side must not be null for non-cancel orders");

            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be > 0 for non-cancel orders");
            }

            if (type == OrderType.LIMIT && price <= 0) {
                throw new IllegalArgumentException("price must be > 0 for limit orders");
            }

            if (type == OrderType.MARKET && price != 0) {
                throw new IllegalArgumentException("price must be 0 for market orders");
            }
        }
    }

    public static OrderRequest limit(String orderId, String userId, Side side, long price, long quantity, long ts) {
        return new OrderRequest(orderId, userId, OrderType.LIMIT, side, price, quantity, ts);
    }

    public static OrderRequest market(String orderId, String userId, Side side, long quantity, long ts) {
        return new OrderRequest(orderId, userId, OrderType.MARKET, side, 0L, quantity, ts);
    }

    public static OrderRequest cancel(String orderId, String userId, long ts) {
        return new OrderRequest(orderId, userId, OrderType.CANCEL, null, 0L, 0L, ts);
    }
}
