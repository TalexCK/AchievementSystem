package dev.shtech.achievement.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaginationTest {
  @Test
  void keepsEmptyCollectionsOnOnePage() {
    assertEquals(1, Pagination.pageCount(0, 28));
    assertEquals(List.of(), Pagination.slice(List.of(), 4, 28));
  }

  @Test
  void slicesAndClampsRequestedPages() {
    List<Integer> values = java.util.stream.IntStream.range(0, 65).boxed().toList();
    assertEquals(3, Pagination.pageCount(values.size(), 28));
    assertEquals(values.subList(28, 56), Pagination.slice(values, 1, 28));
    assertEquals(values.subList(56, 65), Pagination.slice(values, 99, 28));
    assertEquals(values.subList(0, 28), Pagination.slice(values, -5, 28));
  }

  @Test
  void rejectsInvalidArguments() {
    assertThrows(IllegalArgumentException.class, () -> Pagination.pageCount(-1, 28));
    assertThrows(IllegalArgumentException.class, () -> Pagination.pageCount(1, 0));
  }
}
