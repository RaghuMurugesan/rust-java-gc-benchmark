import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLongArray;

public class Service {
    // Histogram buckets in seconds
    private static final double[] BUCKETS = {
        0.080, 0.085, 0.090, 0.095, 0.100,
        0.110, 0.120, 0.150, 0.200, 0.300, 0.500, 1.0
    };

    // Atomic counters: buckets[0..11] + count + sum (as long bits)
    private static final AtomicLongArray bucketCounts = new AtomicLongArray(BUCKETS.length);
    private static final java.util.concurrent.atomic.AtomicLong totalCount = new java.util.concurrent.atomic.AtomicLong(0);
    private static final java.util.concurrent.atomic.DoubleAdder totalSum = new java.util.concurrent.atomic.DoubleAdder();

    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private static final String BACKEND_URL = System.getenv().getOrDefault("BACKEND_URL", "http://localhost:8080/");

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new RequestHandler());
        server.createContext("/metrics", new MetricsHandler());
        server.createContext("/health", ex -> {
            String response = "ok";
            ex.sendResponseHeaders(200, response.length());
            ex.getResponseBody().write(response.getBytes());
            ex.close();
        });

        server.setExecutor(Executors.newFixedThreadPool(200));
        server.start();
        System.out.println("Java service listening on port " + port);
    }

    static class RequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long startNanos = System.nanoTime();

            try {
                // Step 1: Allocate 1MB of short-lived objects (simulates request parsing)
                byte[][] allocations = new byte[1000][];
                for (int i = 0; i < 1000; i++) {
                    allocations[i] = new byte[1024]; // 1KB each = 1MB total
                }

                // Step 2: Touch all allocations (prevents optimization, simulates validation)
                long checksum = 0;
                for (byte[] arr : allocations) {
                    for (int i = 0; i < arr.length; i += 64) {
                        checksum += arr[i];
                    }
                }

                // Step 3: Call downstream backend (78ms sleep)
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BACKEND_URL))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                // Step 4: Send response
                String body = "ok (checksum: " + checksum + ")";
                exchange.sendResponseHeaders(200, body.length());
                exchange.getResponseBody().write(body.getBytes());

            } catch (Exception e) {
                String error = "error: " + e.getMessage();
                exchange.sendResponseHeaders(500, error.length());
                exchange.getResponseBody().write(error.getBytes());
            } finally {
                exchange.close();

                // Record latency
                double durationSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
                recordLatency(durationSeconds);
            }
        }
    }

    static void recordLatency(double seconds) {
        totalCount.incrementAndGet();
        totalSum.add(seconds);

        for (int i = 0; i < BUCKETS.length; i++) {
            if (seconds <= BUCKETS[i]) {
                bucketCounts.incrementAndGet(i);
            }
        }
    }

    static class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder();

            // Histogram buckets
            long cumulative = 0;
            for (int i = 0; i < BUCKETS.length; i++) {
                cumulative += bucketCounts.get(i);
                sb.append(String.format(
                    "http_request_duration_seconds_bucket{le=\"%.3f\"} %d%n",
                    BUCKETS[i], cumulative
                ));
            }
            sb.append(String.format(
                "http_request_duration_seconds_bucket{le=\"+Inf\"} %d%n",
                totalCount.get()
            ));

            sb.append(String.format(
                "http_request_duration_seconds_sum %.6f%n",
                totalSum.sum()
            ));
            sb.append(String.format(
                "http_request_duration_seconds_count %d%n",
                totalCount.get()
            ));

            byte[] response = sb.toString().getBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        }
    }
}
