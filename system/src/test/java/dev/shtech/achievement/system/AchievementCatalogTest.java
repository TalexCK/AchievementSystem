package dev.shtech.achievement.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AchievementCatalogTest {
  @Test
  void ordersCategoriesAndRejectsDuplicates() {
    AchievementCategory first = category("first", 1);
    AchievementCategory second = category("second", 2);
    AchievementCatalog catalog = new AchievementCatalog(List.of(second, first));
    assertEquals(List.of("first", "second"), catalog.categories().stream()
      .map(AchievementCategory::id)
      .toList());
    assertThrows(
      IllegalArgumentException.class,
      () -> new AchievementCatalog(List.of(first, first))
    );
  }

  @Test
  void loadsHiddenAndBadgeSettings(@TempDir Path directory) throws Exception {
    Files.writeString(directory.resolve("achievements.yml"), """
      categories:
        hidden:
          hidden: true
          badge-enabled: false
          display-name: "Secret"
          description:
            - "Secret description"
          material: "BOOK"
          symbol: "?"
          tiers:
            - name: "Secret tier"
              threshold: 1
              color: "#29B6F6"
        defaults:
          display-name: "Defaults"
          description: []
          material: "EMERALD"
          symbol: "✓"
          tiers:
            - name: "Default tier"
              threshold: 1
              color: "#55FF55"
      """);
    AchievementCatalog catalog = AchievementCatalog.load(directory);
    AchievementCategory hidden = catalog.require("hidden");
    AchievementCategory defaults = catalog.require("defaults");
    assertTrue(hidden.hidden());
    assertFalse(hidden.badgeEnabled());
    assertFalse(defaults.hidden());
    assertTrue(defaults.badgeEnabled());
  }

  private static AchievementCategory category(String id, int order) {
    return new AchievementCategory(
      id,
      order,
      false,
      true,
      id,
      List.of(),
      "BOOK",
      "◆",
      List.of(new AchievementTier(1, "Tier", 1, "#AAAAAA", List.of()))
    );
  }
}
