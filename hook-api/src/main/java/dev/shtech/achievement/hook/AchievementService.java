package dev.shtech.achievement.hook;

import dev.shtech.achievement.common.ProgressResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AchievementService extends AutoCloseable {
  CompletableFuture<ProgressResponse> add(
    UUID playerUuid,
    String playerName,
    String categoryId,
    long amount,
    String eventId
  );

  CompletableFuture<ProgressResponse> set(
    UUID playerUuid,
    String playerName,
    String categoryId,
    long amount,
    String eventId
  );

  CompletableFuture<ProgressResponse> max(
    UUID playerUuid,
    String playerName,
    String categoryId,
    long amount,
    String eventId
  );

  @Override
  void close();
}

