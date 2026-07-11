package dev.shtech.achievement.common;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AchievementApiClient {
  private static final int MAX_RESPONSE_LENGTH = 1_048_576;
  private final HttpClient client;
  private final URI baseUri;
  private final String token;
  private final Duration timeout;

  public AchievementApiClient(String apiUrl, String token, Duration timeout) {
    this.baseUri = IdentifierValidator.loopbackHttpUri(apiUrl);
    this.token = IdentifierValidator.token(token);
    this.timeout = Objects.requireNonNull(timeout, "timeout");
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("HTTP timeout must be positive.");
    }
    this.client = HttpClient.newBuilder()
      .connectTimeout(timeout)
      .build();
  }

  public CompletableFuture<HealthResponse> health() {
    return send("GET", "/api/v1/health", null, HealthResponse.class);
  }

  public CompletableFuture<PlayerSnapshot> player(UUID playerUuid, String playerName) {
    String name = URLEncoder.encode(IdentifierValidator.playerName(playerName), StandardCharsets.UTF_8);
    return send(
      "GET",
      "/api/v1/players/" + playerUuid + "?name=" + name,
      null,
      PlayerSnapshot.class
    );
  }

  public CompletableFuture<BadgeSelectionResponse> selectBadges(
    UUID playerUuid,
    BadgeSelectionRequest request
  ) {
    return send(
      "PUT",
      "/api/v1/players/" + playerUuid + "/badges",
      JsonCodec.write(request),
      BadgeSelectionResponse.class
    );
  }

  public CompletableFuture<ProgressResponse> progress(ProgressRequest request) {
    return send("POST", "/api/v1/progress", JsonCodec.write(request), ProgressResponse.class);
  }

  private <T> CompletableFuture<T> send(
    String method,
    String path,
    String body,
    Class<T> responseType
  ) {
    HttpRequest.BodyPublisher publisher = body == null
      ? HttpRequest.BodyPublishers.noBody()
      : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
    HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
      .timeout(timeout)
      .header("Authorization", "Bearer " + token)
      .header("Accept", "application/json")
      .header("Content-Type", "application/json; charset=utf-8")
      .method(method, publisher)
      .build();
    return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
      .thenApply(response -> decode(response, responseType));
  }

  private static <T> T decode(HttpResponse<String> response, Class<T> responseType) {
    String body = response.body();
    if (body.length() > MAX_RESPONSE_LENGTH) {
      throw new AchievementApiException(response.statusCode(), "Achievement API response is too large.");
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      String message = "Achievement API request failed.";
      try {
        ApiError error = JsonCodec.read(body, ApiError.class);
        if (error != null && error.error() != null && !error.error().isBlank()) {
          message = error.error();
        }
      } catch (RuntimeException ignored) {
        message = "Achievement API returned HTTP " + response.statusCode() + ".";
      }
      throw new AchievementApiException(response.statusCode(), message);
    }
    return JsonCodec.read(body, responseType);
  }
}
