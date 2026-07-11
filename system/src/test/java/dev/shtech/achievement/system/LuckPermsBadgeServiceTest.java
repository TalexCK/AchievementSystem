package dev.shtech.achievement.system;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.shtech.achievement.common.BadgeSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class LuckPermsBadgeServiceTest {
  @Test
  void buildsOneResetSafeSuffixInSelectionOrder() {
    LuckPermsBadgeService service = new LuckPermsBadgeService(null, 180);
    String suffix = service.buildSuffix(List.of(
      new BadgeSnapshot(1, "puzzle", "谜境", "◆", "#9E9E9E", 1),
      new BadgeSnapshot(2, "bingo", "Bingo", "✦", "#8E24AA", 10)
    ));
    assertEquals(" &#9E9E9E◆&r &#8E24AA✦&r", suffix);
  }
}

