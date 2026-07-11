package dev.shtech.achievement.system;

import java.util.Locale;

public enum RewardType {
  ACHIEVEMENT_MESSAGE,
  MESSAGE,
  VELOCITY_COMMAND,
  PERMISSION,
  GROUP;

  public static RewardType parse(String value) {
    return switch (value.trim().toLowerCase(Locale.ROOT)) {
      case "achievement-message" -> ACHIEVEMENT_MESSAGE;
      case "message" -> MESSAGE;
      case "velocity-command" -> VELOCITY_COMMAND;
      case "permission" -> PERMISSION;
      case "group" -> GROUP;
      default -> throw new IllegalArgumentException("Unknown reward type: " + value);
    };
  }
}
