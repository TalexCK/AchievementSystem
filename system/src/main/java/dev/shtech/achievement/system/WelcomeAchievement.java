package dev.shtech.achievement.system;

import dev.shtech.achievement.common.ProgressOperation;
import dev.shtech.achievement.common.ProgressRequest;
import java.util.Optional;
import java.util.UUID;

final class WelcomeAchievement {
  private WelcomeAchievement() {
  }

  static boolean shouldGrant(Optional<?> previousServer) {
    return previousServer.isEmpty();
  }

  static ProgressRequest request(UUID playerUuid, String playerName) {
    return new ProgressRequest(
      playerUuid.toString(),
      playerName,
      "welcome",
      ProgressOperation.MAX,
      1,
      "welcome:first-login",
      "achievement_system"
    );
  }
}
