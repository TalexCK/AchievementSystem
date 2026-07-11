package dev.shtech.achievement.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.shtech.achievement.common.BadgeSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class TabBadgeSuffixFormatterTest {
  @Test
  void formatsColoredIconsInDatabaseSlotOrder() {
    String suffix = TabBadgeSuffixFormatter.format(List.of(
      new BadgeSnapshot(2, "bingo", "Bingo King", "✦", "#8e24aa", 10),
      new BadgeSnapshot(1, "puzzle", "谜境", "◆", "#9E9E9E", 1)
    ));
    assertEquals(" &#9E9E9E◆&r &#8E24AA✦&r", suffix);
  }

  @Test
  void clearsEmptySelectionAndKeepsInvalidColorsVisible() {
    assertEquals("", TabBadgeSuffixFormatter.format(List.of()));
    assertEquals(
      " &#FFFFFF✦&r",
      TabBadgeSuffixFormatter.format(List.of(
        new BadgeSnapshot(1, "badge", "Badge", "&✦", "invalid", 1)
      ))
    );
  }
}
