package com.engine;

import com.engine.core.MatchingEngine;
import com.engine.domain.OrderRequest;
import com.engine.web.EngineEventStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ConcurrentLinkedQueue;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Bean
    ConcurrentLinkedQueue<OrderRequest> inboundQueue() {
        return new ConcurrentLinkedQueue<>();
    }

    @Bean
    EngineEventStore engineEventStore() {
        return new EngineEventStore(300);
    }

    @Bean
    MatchingEngine matchingEngine(ConcurrentLinkedQueue<OrderRequest> inboundQueue, EngineEventStore eventStore) {
        return new MatchingEngine(
                inboundQueue,
                eventStore::onTrade,
                eventStore::onCancel,
                eventStore::onLog,
                eventStore::onMarketData,
                50_000L
        );
    }

    @Bean
    CommandLineRunner startupBanner() {
        return args -> System.out.println("Open UI at http://localhost:8080");
    }
}
