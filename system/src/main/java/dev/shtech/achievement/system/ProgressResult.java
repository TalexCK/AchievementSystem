package dev.shtech.achievement.system;

import java.util.List;

public record ProgressResult(
  boolean duplicate,
  long progress,
  int currentTier,
  List<Integer> newlyUnlockedTiers
) {
  public ProgressResult {
    newlyUnlockedTiers = List.copyOf(newlyUnlockedTiers);
  }
}

