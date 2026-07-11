package dev.shtech.achievement.system;

public record RewardDefinition(RewardType type, String value, String description) {
  public RewardDefinition {
    if (value == null || value.isBlank() || value.length() > 512) {
      throw new IllegalArgumentException("Reward value must contain 1 to 512 characters.");
    }
    if (description == null || description.isBlank() || description.length() > 128) {
      throw new IllegalArgumentException("Reward description must contain 1 to 128 characters.");
    }
  }
}

