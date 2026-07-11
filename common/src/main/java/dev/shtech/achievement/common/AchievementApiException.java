package dev.shtech.achievement.common;

public final class AchievementApiException extends RuntimeException {
  private final int statusCode;

  public AchievementApiException(int statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  public int statusCode() {
    return statusCode;
  }
}

