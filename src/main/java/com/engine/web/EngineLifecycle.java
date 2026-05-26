package com.engine.web;

import com.engine.core.MatchingEngine;
import com.engine.domain.OrderRequest;
import com.engine.network.OrderGateway;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public final class EngineLifecycle {
    private final MatchingEngine matchingEngine;
    private final ConcurrentLinkedQueue<OrderRequest> inboundQueue;
    private final int gatewayPort;

    private volatile Thread engineThread;
    private volatile OrderGateway gateway;

    public EngineLifecycle(
            MatchingEngine matchingEngine,
            ConcurrentLinkedQueue<OrderRequest> inboundQueue,
            @Value("${engine.gateway.port:9090}") int gatewayPort
    ) {
        this.matchingEngine = matchingEngine;
        this.inboundQueue = inboundQueue;
        this.gatewayPort = gatewayPort;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        engineThread = Thread.ofPlatform().name("matching-engine-loop").start(matchingEngine::runLoop);
        Thread.ofPlatform().name("order-gateway").start(this::runGateway);
    }

    @PreDestroy
    public void stop() {
        matchingEngine.stop();
        if (engineThread != null) {
            engineThread.interrupt();
        }
        OrderGateway currentGateway = gateway;
        if (currentGateway != null) {
            try {
                currentGateway.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void runGateway() {
        try (OrderGateway orderGateway = new OrderGateway(gatewayPort, inboundQueue)) {
            this.gateway = orderGateway;
            System.out.println("OrderGateway started on port " + gatewayPort);
            orderGateway.start();
        } catch (IOException e) {
            System.err.println("Gateway error: " + e.getMessage());
        }
    }
}
