package dev.shtech.achievement.system;

import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.shtech.achievement.common.ApiError;
import dev.shtech.achievement.common.BadgeSelectionRequest;
import dev.shtech.achievement.common.HealthResponse;
import dev.shtech.achievement.common.JsonCodec;
import dev.shtech.achievement.common.ProgressRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class AchievementHttpServer implements AutoCloseable {
  private final SystemConfig config;
  private final AchievementManager manager;
  private final AchievementDatabase database;
  private final Consumer<String> errorLogger;
  private final HttpServer server;

  public AchievementHttpServer(
    SystemConfig config,
    AchievementManager manager,
    AchievementDatabase database,
    Executor executor,
    Consumer<String> errorLogger
  ) throws IOException {
    this.config = config;
    this.manager = manager;
    this.database = database;
    this.errorLogger = errorLogger;
    InetAddress address = InetAddress.getByName(config.apiHost());
    if (!address.isLoopbackAddress()) {
      throw new IllegalArgumentException("Achievement API must bind to a loopback address.");
    }
    this.server = HttpServer.create(new InetSocketAddress(address, config.apiPort()), 0);
    this.server.setExecutor(executor);
    this.server.createContext("/api/v1", this::handle);
  }

  public void start() {
    server.start();
  }

  private void handle(HttpExchange exchange) {
    try {
      if (!authenticated(exchange)) {
        send(exchange, 401, new ApiError("Authentication failed."));
        return;
      }
      route(exchange);
    } catch (IllegalArgumentException | JsonParseException error) {
      sendQuietly(exchange, 400, new ApiError(error.getMessage()));
    } catch (SQLException error) {
      errorLogger.accept("Achievement API database request failed: " + error.getMessage());
      sendQuietly(exchange, 500, new ApiError("Internal database error."));
    } catch (Exception error) {
      errorLogger.accept("Achievement API request failed: " + rootMessage(error));
      sendQuietly(exchange, 500, new ApiError("Internal server error."));
    } finally {
      exchange.close();
    }
  }

  private void route(HttpExchange exchange) throws IOException, SQLException {
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();
    if (path.equals("/api/v1/health")) {
      requireMethod(method, "GET");
      boolean healthy = database.ping();
      send(
        exchange,
        healthy ? 200 : 503,
        new HealthResponse(healthy ? "ok" : "database-unavailable", manager.catalog().categories().size())
      );
      return;
    }
    if (path.equals("/api/v1/progress")) {
      requireMethod(method, "POST");
      ProgressRequest request = readBody(exchange, ProgressRequest.class);
      send(exchange, 200, manager.progress(request));
      return;
    }
    if (!path.startsWith("/api/v1/players/")) {
      send(exchange, 404, new ApiError("Endpoint not found."));
      return;
    }
    String remainder = path.substring("/api/v1/players/".length());
    if (remainder.endsWith("/badges")) {
      requireMethod(method, "PUT");
      UUID playerUuid = parseUuid(remainder.substring(0, remainder.length() - "/badges".length()));
      BadgeSelectionRequest request = readBody(exchange, BadgeSelectionRequest.class);
      send(exchange, 200, manager.select(playerUuid, request));
      return;
    }
    requireMethod(method, "GET");
    UUID playerUuid = parseUuid(remainder);
    String playerName = query(exchange).get("name");
    if (playerName == null || playerName.isBlank()) {
      throw new IllegalArgumentException("Player name is required.");
    }
    send(exchange, 200, manager.snapshot(playerUuid, playerName));
  }

  private boolean authenticated(HttpExchange exchange) {
    String header = exchange.getRequestHeaders().getFirst("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      return false;
    }
    byte[] expected = config.apiToken().getBytes(StandardCharsets.UTF_8);
    byte[] actual = header.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(expected, actual);
  }

  private <T> T readBody(HttpExchange exchange, Class<T> type) throws IOException {
    String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
    if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
      throw new IllegalArgumentException("Content-Type must be application/json.");
    }
    byte[] body = exchange.getRequestBody().readNBytes(config.maximumBodyBytes() + 1);
    if (body.length > config.maximumBodyBytes()) {
      throw new IllegalArgumentException("Request body is too large.");
    }
    if (body.length == 0) {
      throw new IllegalArgumentException("Request body is required.");
    }
    T value = JsonCodec.read(new String(body, StandardCharsets.UTF_8), type);
    if (value == null) {
      throw new IllegalArgumentException("Request body is invalid.");
    }
    return value;
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException("Invalid player UUID.");
    }
  }

  private static Map<String, String> query(HttpExchange exchange) {
    Map<String, String> values = new HashMap<>();
    String raw = exchange.getRequestURI().getRawQuery();
    if (raw == null || raw.isBlank()) {
      return values;
    }
    for (String pair : raw.split("&")) {
      int separator = pair.indexOf('=');
      if (separator < 0) {
        continue;
      }
      String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
      String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
      values.put(key, value);
    }
    return values;
  }

  private static void requireMethod(String actual, String expected) {
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException("HTTP method is not allowed.");
    }
  }

  private static void send(HttpExchange exchange, int status, Object value) throws IOException {
    byte[] body = JsonCodec.write(value).getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
  }

  private static void sendQuietly(HttpExchange exchange, int status, Object value) {
    try {
      send(exchange, status, value);
    } catch (IOException ignored) {
    }
  }

  private static String rootMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
