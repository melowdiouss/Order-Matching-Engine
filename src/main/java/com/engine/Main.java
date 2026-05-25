package com.engine;

import com.engine.core.MatchingEngine;
import com.engine.domain.CancelAck;
import com.engine.domain.OrderRequest;
import com.engine.domain.Trade;
import com.engine.network.OrderGateway;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9090;

        ConcurrentLinkedQueue<OrderRequest> inboundQueue = new ConcurrentLinkedQueue<>();
        MatchingEngine engine = new MatchingEngine(
                inboundQueue,
                Main::publishTrade,
                Main::publishCancel,
                System.out::println,
                System.out::println,
                50_000L
        );

        Thread engineThread = Thread.ofPlatform().name("matching-engine-loop").start(engine::runLoop);

        try (OrderGateway gateway = new OrderGateway(port, inboundQueue)) {
            System.out.println("OrderGateway started on port " + port);
            gateway.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start gateway", e);
        } finally {
            engine.stop();
            engineThread.interrupt();
        }
    }

    private static void publishTrade(Trade trade) {
        System.out.printf("TRADE resting=%s incoming=%s price=%d qty=%d ts=%d%n",
                trade.restingOrderId(), trade.incomingOrderId(), trade.price(), trade.quantity(), trade.timestampNanos());
    }

    private static void publishCancel(CancelAck cancelAck) {
        System.out.printf("CANCEL order=%s success=%s reason=%s ts=%d%n",
                cancelAck.orderId(), cancelAck.success(), cancelAck.reason(), cancelAck.timestampNanos());
    }
}
