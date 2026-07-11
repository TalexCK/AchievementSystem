package dev.shtech.achievement.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProgressBarTest {
  @Test
  void calculatesBoundedSegments() {
    assertEquals(0, ProgressBar.filledSegments(0, 10, 7));
    assertEquals(3, ProgressBar.filledSegments(5, 10, 7));
    assertEquals(7, ProgressBar.filledSegments(10, 10, 7));
    assertEquals(7, ProgressBar.filledSegments(50, 10, 7));
  }

  @Test
  void treatsZeroTargetAsComplete() {
    assertEquals(7, ProgressBar.filledSegments(0, 0, 7));
  }

  @Test
  void rejectsInvalidArguments() {
    assertThrows(IllegalArgumentException.class, () -> ProgressBar.filledSegments(-1, 10, 7));
    assertThrows(IllegalArgumentException.class, () -> ProgressBar.filledSegments(1, 10, 0));
  }
}
