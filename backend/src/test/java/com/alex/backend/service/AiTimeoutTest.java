package com.alex.backend.service;

import com.alex.backend.client.AiClient;
import com.alex.backend.common.ErrorCode;
import com.alex.backend.exception.BusinessException;
import com.alex.backend.request.AiChatRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import java.net.InetSocketAddress;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class AiTimeoutTest {
    @Test
    void chatAndIndexHaveIndependentTimeouts() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = java.util.concurrent.Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(500);
                String json = exchange.getRequestURI().getPath().equals("/chat") ? "{\"answer\":\"ok\",\"sources\":[]}" : "{\"count\":1}";
                byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            catch (java.io.IOException ignored) { }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var client = new AiClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofSeconds(1), Duration.ofMillis(100), Duration.ofSeconds(3));
            assertEquals(ErrorCode.AI_SERVICE_TIMEOUT,
                    assertThrows(BusinessException.class, () -> client.chat(new AiChatRequest())).getErrorCode());
            assertDoesNotThrow(() -> client.upsertVectors(1L, java.util.List.of("chunk")));
            var reverse = new AiClient(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofMillis(100));
            assertEquals("ok", reverse.chat(new AiChatRequest()).getAnswer());
            assertEquals(ErrorCode.AI_SERVICE_TIMEOUT,
                    assertThrows(BusinessException.class, () -> reverse.upsertVectors(1L, java.util.List.of("chunk"))).getErrorCode());
        } finally { server.stop(0); executor.shutdownNow(); }
    }
}
