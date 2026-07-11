package dev.shtech.achievement.common;

import java.util.List;

public record PlayerSnapshot(
  String playerUuid,
  String playerName,
  int unlockedTiers,
  int totalTiers,
  List<BadgeSnapshot> selectedBadges,
  List<CategorySnapshot> categories
) {
  public PlayerSnapshot {
    selectedBadges = selectedBadges == null ? List.of() : List.copyOf(selectedBadges);
    categories = categories == null ? List.of() : List.copyOf(categories);
  }
}

