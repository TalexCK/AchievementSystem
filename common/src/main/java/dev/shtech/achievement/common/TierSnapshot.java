package dev.shtech.achievement.common;

import java.util.List;

public record TierSnapshot(
  int level,
  String name,
  long threshold,
  String color,
  boolean unlocked,
  long unlockedPlayers,
  String firstUnlockerName,
  List<String> rewardDescriptions
) {
  public TierSnapshot {
    rewardDescriptions = rewardDescriptions == null ? List.of() : List.copyOf(rewardDescriptions);
  }
}
