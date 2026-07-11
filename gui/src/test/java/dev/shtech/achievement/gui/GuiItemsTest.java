package dev.shtech.achievement.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GuiItemsTest {
  @Test
  void displaysQuestionMarksWithoutAFirstUnlocker() {
    assertEquals("???", GuiItems.firstUnlockerName(null));
    assertEquals("???", GuiItems.firstUnlockerName("  "));
  }

  @Test
  void displaysTheCurrentFirstUnlockerName() {
    assertEquals("TalexCK", GuiItems.firstUnlockerName("TalexCK"));
  }
}
