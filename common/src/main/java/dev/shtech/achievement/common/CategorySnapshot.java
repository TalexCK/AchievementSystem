package dev.shtech.achievement.common;

import java.util.List;

public record CategorySnapshot(
  String id,
  boolean hidden,
  boolean discovered,
  boolean badgeEnabled,
  String displayName,
  List<String> description,
  String material,
  String symbol,
  long progress,
  int currentTier,
  long nextThreshold,
  long unlockedPlayers,
  boolean selected,
  List<TierSnapshot> tiers
) {
  public CategorySnapshot {
    description = description == null ? List.of() : List.copyOf(description);
    tiers = tiers == null ? List.of() : List.copyOf(tiers);
  }
}
