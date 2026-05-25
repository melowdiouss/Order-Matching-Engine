package com.engine.domain;

import java.util.Objects;

public record Trade(
        String restingOrderId,
        String incomingOrderId,
        long price,
        long quantity,
        long timestampNanos
) {
    public Trade {
        restingOrderId = Objects.requireNonNull(restingOrderId, "restingOrderId must not be null").trim();
        if (restingOrderId.isEmpty()) {
            throw new IllegalArgumentException("restingOrderId must not be blank");
        }

        incomingOrderId = Objects.requireNonNull(incomingOrderId, "incomingOrderId must not be null").trim();
        if (incomingOrderId.isEmpty()) {
            throw new IllegalArgumentException("incomingOrderId must not be blank");
        }

        if (price <= 0) {
            throw new IllegalArgumentException("price must be > 0");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }

        if (timestampNanos < 0) {
            throw new IllegalArgumentException("timestampNanos must be >= 0");
        }
    }
}
