package dev.shtech.achievement.hook;

import dev.shtech.achievement.common.AchievementApiClient;
import dev.shtech.achievement.common.AchievementApiException;
import dev.shtech.achievement.common.IdentifierValidator;
import dev.shtech.achievement.common.ProgressOperation;
import dev.shtech.achievement.common.ProgressRequest;
import dev.shtech.achievement.common.ProgressResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HttpAchievementService implements AchievementService {
  private final AchievementApiClient client;
  private final String source;
  private final int maximumAttempts;
  private final ScheduledExecutorService retryExecutor;
  private final AtomicBoolean closed = new AtomicBoolean();

  public HttpAchievementService(
    String apiUrl,
    String token,
    Duration timeout,
    String source,
    int maximumAttempts
  ) {
    this.client = new AchievementApiClient(apiUrl, token, timeout);
    this.source = IdentifierValidator.identifier(source, "source");
    if (maximumAttempts < 1 || maximumAttempts > 10) {
      throw new IllegalArgumentException("Retry attempts must be between 1 and 10.");
    }
    this.maximumAttempts = maximumAttempts;
    ThreadFactory factory = task -> {
      Thread thread = new Thread(task, "achievement-hook-retry");
      thread.setDaemon(true);
      return thread;
    };
    this.retryExecutor = Executors.newSingleThreadScheduledExecutor(factory);
  }

  @Override
  public CompletableFuture<ProgressResponse> add(
    UUID playerUuid,
    String playerName,
    String categoryId,
    long amount,
    String eventId
  ) {
    return submit(playerUuid, playerName, categoryId, ProgressOperation.ADD, amount, eventId);
  }

  @Override
  public CompletableFuture<ProgressResponse> set(
    UUID playerUuid,
    String playerName,
    String categoryId,
    long amount,
    String eventId
  ) {
    return submit(playerUuid, playerName, categoryId, ProgressOperation.SET, amount, eventId);
  }

  @Override
  public CompletableFuture<ProgressResponse> max(
    UUID playerUuid,
    String playerName,
    String categoryId,
    long amount,
    String eventId
  ) {
    return submit(playerUuid, playerName, categoryId, ProgressOperation.MAX, amount, eventId);
  }

  private CompletableFuture<ProgressResponse> submit(
    UUID playerUuid,
    String playerName,
    String categoryId,
    ProgressOperation operation,
    long amount,
    String eventId
  ) {
    if (closed.get()) {
      return CompletableFuture.failedFuture(
        new IllegalStateException("AchievementHook is closed.")
      );
    }
    ProgressRequest request = new ProgressRequest(
      Objects.requireNonNull(playerUuid, "playerUuid").toString(),
      playerName,
      categoryId,
      operation,
      amount,
      eventId == null || eventId.isBlank() ? UUID.randomUUID().toString() : eventId,
      source
    ).validated();
    CompletableFuture<ProgressResponse> result = new CompletableFuture<>();
    attempt(request, 1, result);
    return result;
  }

  private void attempt(
    ProgressRequest request,
    int attempt,
    CompletableFuture<ProgressResponse> result
  ) {
    if (closed.get()) {
      result.completeExceptionally(new IllegalStateException("AchievementHook is closed."));
      return;
    }
    client.progress(request).whenComplete((response, error) -> {
      if (error == null) {
        result.complete(response);
        return;
      }
      Throwable cause = unwrap(error);
      if (attempt >= maximumAttempts || !retryable(cause)) {
        result.completeExceptionally(cause);
        return;
      }
      long delayMillis = Math.min(8_000L, 500L << (attempt - 1));
      retryExecutor.schedule(
        () -> attempt(request, attempt + 1, result),
        delayMillis,
        TimeUnit.MILLISECONDS
      );
    });
  }

  private static boolean retryable(Throwable error) {
    return !(error instanceof AchievementApiException apiError)
      || apiError.statusCode() >= 500
      || apiError.statusCode() == 429;
  }

  private static Throwable unwrap(Throwable error) {
    Throwable current = error;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      retryExecutor.shutdownNow();
    }
  }
}
