package dev.shtech.achievement.hook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.shtech.achievement.common.ProgressResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class AchievementHookTest {
  @Test
  void installsAndUninstallsOneService() {
    AchievementService service = new StubService();
    assertTrue(AchievementHook.install(service));
    assertSame(service, AchievementHook.service());
    assertFalse(AchievementHook.install(new StubService()));
    AchievementHook.uninstall(service);
    assertTrue(AchievementHook.optionalService().isEmpty());
  }

  private static final class StubService implements AchievementService {
    @Override
    public CompletableFuture<ProgressResponse> add(
      UUID playerUuid,
      String playerName,
      String categoryId,
      long amount,
      String eventId
    ) {
      return response();
    }

    @Override
    public CompletableFuture<ProgressResponse> set(
      UUID playerUuid,
      String playerName,
      String categoryId,
      long amount,
      String eventId
    ) {
      return response();
    }

    @Override
    public CompletableFuture<ProgressResponse> max(
      UUID playerUuid,
      String playerName,
      String categoryId,
      long amount,
      String eventId
    ) {
      return response();
    }

    private CompletableFuture<ProgressResponse> response() {
      return CompletableFuture.completedFuture(
        new ProgressResponse(true, false, 1, 1, List.of(1), null)
      );
    }

    @Override
    public void close() {
    }
  }
}

