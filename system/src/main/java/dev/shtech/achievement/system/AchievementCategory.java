package dev.shtech.achievement.system;

import dev.shtech.achievement.common.IdentifierValidator;
import java.util.List;

public record AchievementCategory(
  String id,
  int order,
  boolean hidden,
  boolean badgeEnabled,
  String displayName,
  List<String> description,
  String material,
  String symbol,
  List<AchievementTier> tiers
) {
  public AchievementCategory {
    id = IdentifierValidator.identifier(id, "categoryId");
    if (displayName == null || displayName.isBlank() || displayName.length() > 64) {
      throw new IllegalArgumentException("Category display name must contain 1 to 64 characters.");
    }
    description = description == null ? List.of() : List.copyOf(description);
    if (description.size() > 8 || description.stream().anyMatch(line -> line.length() > 96)) {
      throw new IllegalArgumentException("Category description is too long.");
    }
    if (material == null || !material.matches("[A-Z0-9_]{1,64}")) {
      throw new IllegalArgumentException("Invalid category material.");
    }
    if (symbol == null || symbol.isBlank() || symbol.codePointCount(0, symbol.length()) > 4) {
      throw new IllegalArgumentException("Category symbol must contain 1 to 4 characters.");
    }
    tiers = tiers == null ? List.of() : List.copyOf(tiers);
    if (tiers.isEmpty() || tiers.size() > 10) {
      throw new IllegalArgumentException("Category must contain 1 to 10 tiers.");
    }
    long previous = 0;
    for (int index = 0; index < tiers.size(); index++) {
      AchievementTier tier = tiers.get(index);
      if (tier.level() != index + 1 || tier.threshold() <= previous) {
        throw new IllegalArgumentException("Category tiers must be sequential with increasing thresholds.");
      }
      previous = tier.threshold();
    }
  }

  public int currentTier(long progress) {
    int current = 0;
    for (AchievementTier tier : tiers) {
      if (progress < tier.threshold()) {
        break;
      }
      current = tier.level();
    }
    return current;
  }

  public long nextThreshold(long progress) {
    for (AchievementTier tier : tiers) {
      if (progress < tier.threshold()) {
        return tier.threshold();
      }
    }
    return -1;
  }

  public AchievementTier tier(int level) {
    if (level < 1 || level > tiers.size()) {
      throw new IllegalArgumentException("Unknown achievement tier.");
    }
    return tiers.get(level - 1);
  }
}
