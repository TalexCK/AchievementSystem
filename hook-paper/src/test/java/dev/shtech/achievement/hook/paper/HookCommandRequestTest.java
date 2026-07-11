package dev.shtech.achievement.hook.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class HookCommandRequestTest {
  @Test
  void parsesAddRequest() {
    HookCommandRequest request = HookCommandRequest.parse(
      new String[] {"add", "Alex", "puzzle_maps", "1"}
    );

    assertEquals(HookCommandRequest.Operation.ADD, request.operation());
    assertEquals("Alex", request.player());
    assertEquals("puzzle_maps", request.categoryId());
    assertEquals(1, request.amount());
    assertNull(request.eventId());
  }

  @Test
  void parsesIdempotentMaxRequest() {
    HookCommandRequest request = HookCommandRequest.parse(
      new String[] {"max", "Alex", "puzzle_maps", "5", "puzzle:map-one"}
    );

    assertEquals(HookCommandRequest.Operation.MAX, request.operation());
    assertEquals("puzzle:map-one", request.eventId());
  }

  @Test
  void rejectsInvalidAmounts() {
    assertThrows(
      IllegalArgumentException.class,
      () -> HookCommandRequest.parse(new String[] {"add", "Alex", "puzzle_maps", "0"})
    );
    assertThrows(
      IllegalArgumentException.class,
      () -> HookCommandRequest.parse(new String[] {"set", "Alex", "puzzle_maps", "-1"})
    );
  }
}
