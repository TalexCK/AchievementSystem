package dev.shtech.achievement.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class AchievementCategoryTest {
  @Test
  void resolvesCurrentAndNextTier() {
    AchievementCategory category = category(List.of(
      tier(1, 1),
      tier(2, 3),
      tier(3, 5)
    ));
    assertEquals(0, category.currentTier(0));
    assertEquals(2, category.currentTier(4));
    assertEquals(5, category.nextThreshold(4));
    assertEquals(-1, category.nextThreshold(5));
  }

  @Test
  void rejectsNonIncreasingThresholds() {
    assertThrows(
      IllegalArgumentException.class,
      () -> category(List.of(tier(1, 3), tier(2, 3)))
    );
  }

  private static AchievementCategory category(List<AchievementTier> tiers) {
    return new AchievementCategory(
      "puzzle_maps",
      1,
      false,
      true,
      "谜境探索",
      List.of("完成解谜地图"),
      "AMETHYST_SHARD",
      "◆",
      tiers
    );
  }

  private static AchievementTier tier(int level, long threshold) {
    return new AchievementTier(level, "Tier " + level, threshold, "#AA55FF", List.of());
  }
}
