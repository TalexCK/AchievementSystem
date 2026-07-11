package dev.shtech.achievement.system;

import java.util.UUID;

public record AdminAchievementResult(
  UUID playerUuid,
  String playerName,
  String categoryId,
  long progress,
  int currentTier
) {
}
