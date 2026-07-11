package dev.shtech.achievement.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class BadgeSelectionRequestTest {
  @Test
  void preservesSelectionOrder() {
    BadgeSelectionRequest request = new BadgeSelectionRequest(
      List.of("Puzzle_Maps", "bingo_wins")
    ).validated(3);
    assertEquals(List.of("puzzle_maps", "bingo_wins"), request.categoryIds());
  }

  @Test
  void rejectsDuplicateAndOversizedSelections() {
    assertThrows(
      IllegalArgumentException.class,
      () -> new BadgeSelectionRequest(List.of("puzzle", "puzzle")).validated(3)
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> new BadgeSelectionRequest(List.of("a", "b", "c", "d")).validated(3)
    );
  }
}
