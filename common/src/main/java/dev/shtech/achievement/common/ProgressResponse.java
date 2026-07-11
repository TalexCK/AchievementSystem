package dev.shtech.achievement.common;

import java.util.List;

public record ProgressResponse(
  boolean accepted,
  boolean duplicate,
  long progress,
  int currentTier,
  List<Integer> newlyUnlockedTiers,
  String error
) {
  public ProgressResponse {
    newlyUnlockedTiers = newlyUnlockedTiers == null ? List.of() : List.copyOf(newlyUnlockedTiers);
  }

  public static ProgressResponse failure(String error) {
    return new ProgressResponse(false, false, 0, 0, List.of(), error);
  }
}

