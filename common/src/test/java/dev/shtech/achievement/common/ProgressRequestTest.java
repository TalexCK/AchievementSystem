package dev.shtech.achievement.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProgressRequestTest {
  @Test
  void validatesProgressRequest() {
    ProgressRequest request = new ProgressRequest(
      UUID.randomUUID().toString(),
      "TalexCK",
      "Puzzle_Maps",
      ProgressOperation.ADD,
      1,
      "map:test-1",
      "Puzzle_Server"
    ).validated();
    assertEquals("puzzle_maps", request.categoryId());
    assertEquals("puzzle_server", request.source());
  }

  @Test
  void rejectsNonPositiveAdd() {
    ProgressRequest request = new ProgressRequest(
      UUID.randomUUID().toString(),
      "TalexCK",
      "puzzle_maps",
      ProgressOperation.ADD,
      0,
      null,
      "puzzle"
    );
    assertThrows(IllegalArgumentException.class, request::validated);
  }
}

