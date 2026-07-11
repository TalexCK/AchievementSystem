package dev.shtech.achievement.system;

import java.util.UUID;

public record RewardDelivery(
  long id,
  UUID playerUuid,
  String playerName,
  String categoryId,
  int tier,
  RewardType type,
  String value,
  int attempts
) {
}

