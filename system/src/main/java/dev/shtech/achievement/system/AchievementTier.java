package dev.shtech.achievement.system;

import java.util.List;

public record AchievementTier(
  int level,
  String name,
  long threshold,
  String color,
  List<RewardDefinition> rewards
) {
  public AchievementTier {
    if (level < 1 || level > 10) {
      throw new IllegalArgumentException("Tier level must be between 1 and 10.");
    }
    if (name == null || name.isBlank() || name.length() > 64) {
      throw new IllegalArgumentException("Tier name must contain 1 to 64 characters.");
    }
    if (threshold < 1) {
      throw new IllegalArgumentException("Tier threshold must be positive.");
    }
    if (color == null || !color.matches("#[0-9A-Fa-f]{6}")) {
      throw new IllegalArgumentException("Tier color must use #RRGGBB format.");
    }
    color = color.toUpperCase();
    rewards = rewards == null ? List.of() : List.copyOf(rewards);
  }
}
