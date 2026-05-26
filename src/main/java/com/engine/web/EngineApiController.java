package com.engine.web;

import com.engine.domain.OrderRequest;
import com.engine.domain.OrderType;
import com.engine.domain.Side;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

@RestController
@RequestMapping("/api")
public final class EngineApiController {
    private final ConcurrentLinkedQueue<OrderRequest> inboundQueue;
    private final EngineEventStore eventStore;

    public EngineApiController(ConcurrentLinkedQueue<OrderRequest> inboundQueue, EngineEventStore eventStore) {
        this.inboundQueue = inboundQueue;
        this.eventStore = eventStore;
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> submitOrder(@RequestBody OrderSubmission submission) {
        try {
            OrderRequest request = toOrderRequest(submission);
            boolean queued = inboundQueue.offer(request);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("accepted", queued);
            payload.put("orderId", request.orderId());
            payload.put("type", request.type().name());
            payload.put("queuedAtNanos", request.timestampNanos());
            return ResponseEntity.ok(payload);
        } catch (RuntimeException ex) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("accepted", false);
            payload.put("error", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(payload);
        }
    }

    @GetMapping("/book")
    public Map<String, Object> book(@RequestParam(defaultValue = "10") int depth) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("depth", Math.max(1, depth));
        payload.put("snapshot", eventStore.latestSnapshot());
        return payload;
    }

    @GetMapping("/events")
    public Map<String, List<String>> events(@RequestParam(defaultValue = "30") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return Map.of("events", eventStore.recentEvents(safeLimit));
    }

    private static OrderRequest toOrderRequest(OrderSubmission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        OrderType type = parseOrderType(submission.type);
        String orderId = required(submission.orderId, "orderId");
        String userId = required(submission.userId, "userId");
        long now = System.nanoTime();

        return switch (type) {
            case CANCEL -> OrderRequest.cancel(orderId, userId, now);
            case MARKET -> {
                Side side = parseSide(submission.side);
                if (submission.quantity == null) {
                    throw new IllegalArgumentException("quantity is required for MARKET");
                }
                yield OrderRequest.market(orderId, userId, side, submission.quantity, now);
            }
            case LIMIT -> {
                Side side = parseSide(submission.side);
                if (submission.price == null) {
                    throw new IllegalArgumentException("price is required for LIMIT");
                }
                if (submission.quantity == null) {
                    throw new IllegalArgumentException("quantity is required for LIMIT");
                }
                yield OrderRequest.limit(orderId, userId, side, submission.price, submission.quantity, now);
            }
        };
    }

    private static OrderType parseOrderType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        return OrderType.valueOf(rawType.trim().toUpperCase());
    }

    private static Side parseSide(String rawSide) {
        if (rawSide == null || rawSide.isBlank()) {
            throw new IllegalArgumentException("side is required for LIMIT/MARKET");
        }
        return Side.valueOf(rawSide.trim().toUpperCase());
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static final class OrderSubmission {
        public String type;
        public String orderId;
        public String userId;
        public String side;
        public Long price;
        public Long quantity;
    }
}
