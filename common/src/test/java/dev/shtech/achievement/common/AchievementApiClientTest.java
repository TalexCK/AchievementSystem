package dev.shtech.achievement.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AchievementApiClientTest {
  private static final String TOKEN = "0123456789abcdef0123456789abcdef";
  private HttpServer server;
  private String apiUrl;

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/health", exchange -> {
      if (!("Bearer " + TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
        exchange.sendResponseHeaders(401, -1);
        exchange.close();
        return;
      }
      byte[] body = JsonCodec.write(new HealthResponse("ok", 2))
        .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.createContext("/api/v1/progress", exchange -> {
      byte[] body = JsonCodec.write(new ApiError("Category is disabled."))
        .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(400, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    apiUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void sendsBearerTokenAndDecodesResponse() {
    AchievementApiClient client = new AchievementApiClient(apiUrl, TOKEN, Duration.ofSeconds(2));
    HealthResponse response = client.health().join();
    assertEquals("ok", response.status());
    assertEquals(2, response.categories());
  }

  @Test
  void exposesStructuredApiErrors() {
    AchievementApiClient client = new AchievementApiClient(apiUrl, TOKEN, Duration.ofSeconds(2));
    ProgressRequest request = new ProgressRequest(
      "28d4ee2b-da4e-33dd-876c-995f9c0c4f64",
      "TalexCK",
      "puzzle_maps",
      ProgressOperation.ADD,
      1,
      "test:event",
      "test"
    );
    CompletionException error = assertThrows(
      CompletionException.class,
      () -> client.progress(request).join()
    );
    AchievementApiException cause = (AchievementApiException) error.getCause();
    assertEquals(400, cause.statusCode());
    assertEquals("Category is disabled.", cause.getMessage());
  }
}
