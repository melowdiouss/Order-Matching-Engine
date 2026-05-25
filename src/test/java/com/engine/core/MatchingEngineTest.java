package com.engine.core;

import com.engine.domain.CancelAck;
import com.engine.domain.OrderRequest;
import com.engine.domain.Side;
import com.engine.domain.Trade;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingEngineTest {

    @Test
    void shouldMatchUsingPriceTimePriority() {
        List<Trade> published = new ArrayList<>();
        List<CancelAck> cancels = new ArrayList<>();
        MatchingEngine engine = new MatchingEngine(new ConcurrentLinkedQueue<>(), published::add, cancels::add);

        engine.process(OrderRequest.limit("S1", "U2", Side.SELL, 100L, 50L, 1L));
        engine.process(OrderRequest.limit("S2", "U3", Side.SELL, 100L, 30L, 2L));

        List<Trade> trades = engine.process(OrderRequest.limit("B1", "U1", Side.BUY, 100L, 60L, 3L));

        assertEquals(2, trades.size());
        assertEquals("S1", trades.get(0).restingOrderId());
        assertEquals(50L, trades.get(0).quantity());
        assertEquals("S2", trades.get(1).restingOrderId());
        assertEquals(10L, trades.get(1).quantity());
        assertEquals(2, published.size());
        assertTrue(cancels.isEmpty());
        assertEquals(20L, engine.asks().view().get(100L).peekFirst().remainingQuantity());
    }

    @Test
    void marketBuyOnEmptyBookShouldCancelRemainder() {
        List<CancelAck> cancels = new ArrayList<>();
        MatchingEngine engine = new MatchingEngine(new ConcurrentLinkedQueue<>(), trade -> {}, cancels::add);

        List<Trade> trades = engine.process(OrderRequest.market("MB1", "U1", Side.BUY, 10L, 1L));

        assertTrue(trades.isEmpty());
        assertEquals(1, cancels.size());
        assertEquals("MB1", cancels.getFirst().orderId());
        assertTrue(cancels.getFirst().success());
    }

    @Test
    void cancelPartiallyFilledOrderShouldRemoveRemaining() {
        List<CancelAck> cancels = new ArrayList<>();
        MatchingEngine engine = new MatchingEngine(new ConcurrentLinkedQueue<>(), trade -> {}, cancels::add);

        engine.process(OrderRequest.limit("B1", "U1", Side.BUY, 105L, 100L, 1L));
        engine.process(OrderRequest.limit("S1", "U2", Side.SELL, 105L, 30L, 2L));

        assertNotNull(engine.bids().view().get(105L));
        assertEquals(70L, engine.bids().view().get(105L).peekFirst().remainingQuantity());

        engine.process(OrderRequest.cancel("B1", "U1", 3L));

        assertTrue(engine.bids().isEmpty());
        assertEquals("B1", cancels.getFirst().orderId());
        assertTrue(cancels.getFirst().success());
    }

    @Test
    void cancelAlreadyFilledOrderShouldRejectGracefully() {
        List<CancelAck> cancels = new ArrayList<>();
        MatchingEngine engine = new MatchingEngine(new ConcurrentLinkedQueue<>(), trade -> {}, cancels::add);

        engine.process(OrderRequest.limit("B1", "U1", Side.BUY, 100L, 10L, 1L));
        engine.process(OrderRequest.limit("S1", "U2", Side.SELL, 100L, 10L, 2L));
        engine.process(OrderRequest.cancel("B1", "U1", 3L));

        assertEquals(1, cancels.size());
        assertEquals("B1", cancels.getFirst().orderId());
        assertFalse(cancels.getFirst().success());
    }

    @Test
    void selfTradePreventionShouldSkipSameUserRestingOrder() {
        List<Trade> published = new ArrayList<>();
        MatchingEngine engine = new MatchingEngine(new ConcurrentLinkedQueue<>(), published::add, cancel -> {});

        engine.process(OrderRequest.limit("S1", "U1", Side.SELL, 100L, 20L, 1L));
        engine.process(OrderRequest.limit("S2", "U2", Side.SELL, 100L, 20L, 2L));

        List<Trade> trades = engine.process(OrderRequest.limit("B1", "U1", Side.BUY, 100L, 15L, 3L));

        assertEquals(1, trades.size());
        assertEquals("S2", trades.getFirst().restingOrderId());
        assertEquals(15L, trades.getFirst().quantity());
        assertEquals(1, published.size());
    }

    @Test
    void singleTickSpreadShouldNotCross() {
        MatchingEngine engine = new MatchingEngine(new ConcurrentLinkedQueue<>(), trade -> {}, cancel -> {});

        engine.process(OrderRequest.limit("B1", "U1", Side.BUY, 100L, 5L, 1L));
        List<Trade> trades = engine.process(OrderRequest.limit("S1", "U2", Side.SELL, 101L, 5L, 2L));

        assertTrue(trades.isEmpty());
        assertNotNull(engine.bids().view().get(100L));
        assertNotNull(engine.asks().view().get(101L));
    }

    @Test
    void multiLevelSweepShouldConsumeAcrossPriceLevels() {
        MatchingEngine engine = new MatchingEngine(new ConcurrentLinkedQueue<>(), trade -> {}, cancel -> {});

        engine.process(OrderRequest.limit("S1", "U2", Side.SELL, 100L, 10L, 1L));
        engine.process(OrderRequest.limit("S2", "U3", Side.SELL, 101L, 10L, 2L));
        engine.process(OrderRequest.limit("S3", "U4", Side.SELL, 102L, 10L, 3L));

        List<Trade> trades = engine.process(OrderRequest.market("B1", "U1", Side.BUY, 25L, 4L));

        assertEquals(3, trades.size());
        assertEquals(100L, trades.get(0).price());
        assertEquals(101L, trades.get(1).price());
        assertEquals(102L, trades.get(2).price());
        assertEquals(5L, engine.asks().view().get(102L).peekFirst().remainingQuantity());
    }

    @Test
    void exactQuantityMatchShouldRemovePriceLevel() {
        MatchingEngine engine = new MatchingEngine(new ConcurrentLinkedQueue<>(), trade -> {}, cancel -> {});

        engine.process(OrderRequest.limit("S1", "U2", Side.SELL, 100L, 50L, 1L));
        List<Trade> trades = engine.process(OrderRequest.limit("B1", "U1", Side.BUY, 100L, 50L, 2L));

        assertEquals(1, trades.size());
        assertTrue(engine.asks().isEmpty());
    }
}
