package com.engine.domain;

import java.util.Objects;

public record CancelAck(
        String orderId,
        String reason,
        long timestampNanos,
        boolean success
) {
    public CancelAck {
        orderId = Objects.requireNonNull(orderId, "orderId must not be null").trim();
        if (orderId.isEmpty()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        reason = Objects.requireNonNull(reason, "reason must not be null").trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (timestampNanos < 0) {
            throw new IllegalArgumentException("timestampNanos must be >= 0");
        }
    }
}
