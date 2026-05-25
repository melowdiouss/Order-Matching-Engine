package com.engine.network;

import com.engine.domain.OrderRequest;
import com.engine.domain.OrderType;
import com.engine.domain.Side;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OrderGateway implements AutoCloseable {
    private final int port;
    private final ConcurrentLinkedQueue<OrderRequest> inboundQueue;
    private final AtomicBoolean running;
    private ServerSocket serverSocket;
    private ExecutorService clientExecutor;

    public OrderGateway(int port, ConcurrentLinkedQueue<OrderRequest> inboundQueue) {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        this.port = port;
        this.inboundQueue = Objects.requireNonNull(inboundQueue, "inboundQueue must not be null");
        this.running = new AtomicBoolean(false);
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Gateway already running");
        }

        serverSocket = new ServerSocket(port);
        clientExecutor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

        while (running.get()) {
            try {
                Socket client = serverSocket.accept();
                clientExecutor.submit(() -> handleClient(client));
            } catch (IOException e) {
                if (running.get()) {
                    throw e;
                }
            }
        }
    }

    private void handleClient(Socket client) {
        try (Socket socket = client;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {

            writer.println("CONNECTED");
            writer.println("LIMIT format: LIMIT,orderId,userId,BUY|SELL,price,quantity");
            writer.println("MARKET format: MARKET,orderId,userId,BUY|SELL,quantity");
            writer.println("CANCEL format: CANCEL,orderId,userId");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    OrderRequest request = parseLine(line);
                    inboundQueue.offer(request);
                    writer.println("ACCEPTED " + request.orderId());
                } catch (RuntimeException parseError) {
                    writer.println("REJECTED " + parseError.getMessage());
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static OrderRequest parseLine(String line) {
        String[] parts = line.split(",");
        if (parts.length < 3) {
            throw new IllegalArgumentException("expected at least 3 fields");
        }

        OrderType type = OrderType.valueOf(parts[0].trim().toUpperCase());
        String orderId = parts[1].trim();
        String userId = parts[2].trim();
        long now = System.nanoTime();

        return switch (type) {
            case CANCEL -> OrderRequest.cancel(orderId, userId, now);
            case MARKET -> {
                if (parts.length != 5) {
                    throw new IllegalArgumentException("MARKET requires 5 fields");
                }
                Side side = Side.valueOf(parts[3].trim().toUpperCase());
                long quantity = Long.parseLong(parts[4].trim());
                yield OrderRequest.market(orderId, userId, side, quantity, now);
            }
            case LIMIT -> {
                if (parts.length != 6) {
                    throw new IllegalArgumentException("LIMIT requires 6 fields");
                }
                Side side = Side.valueOf(parts[3].trim().toUpperCase());
                long price = Long.parseLong(parts[4].trim());
                long quantity = Long.parseLong(parts[5].trim());
                yield OrderRequest.limit(orderId, userId, side, price, quantity, now);
            }
        };
    }

    @Override
    public void close() throws IOException {
        running.set(false);
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
        if (clientExecutor != null) {
            clientExecutor.shutdown();
        }
    }
}
