package dev.shtech.achievement.hook;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import dev.shtech.achievement.common.JsonCodec;
import dev.shtech.achievement.common.ProgressRequest;
import dev.shtech.achievement.common.ProgressResponse;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpAchievementServiceTest {
  private static final String TOKEN = "0123456789abcdef0123456789abcdef";
  private HttpServer server;
  private String apiUrl;

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    apiUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void retriesWithTheSameGeneratedEventId() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    AtomicReference<String> firstEventId = new AtomicReference<>();
    server.createContext("/api/v1/progress", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      ProgressRequest request = JsonCodec.read(body, ProgressRequest.class);
      firstEventId.compareAndSet(null, request.eventId());
      assertEquals(firstEventId.get(), request.eventId());
      if (attempts.incrementAndGet() == 1) {
        exchange.sendResponseHeaders(503, -1);
        exchange.close();
        return;
      }
      byte[] response = JsonCodec.write(
        new ProgressResponse(true, false, 1, 1, List.of(1), null)
      ).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    try (HttpAchievementService service = new HttpAchievementService(
      apiUrl,
      TOKEN,
      Duration.ofSeconds(2),
      "test_server",
      2
    )) {
      ProgressResponse response = service.add(
        UUID.randomUUID(),
        "TalexCK",
        "puzzle_maps",
        1,
        null
      ).join();
      assertEquals(1, response.progress());
      assertEquals(2, attempts.get());
    }
  }
}
