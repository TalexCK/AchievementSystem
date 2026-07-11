package dev.shtech.achievement.common;

public record BadgeSnapshot(
  int slot,
  String categoryId,
  String displayName,
  String symbol,
  String color,
  int tier
) {
}

