package dev.shtech.achievement.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class IdentifierValidatorTest {
  @Test
  void normalizesIdentifiers() {
    assertEquals("puzzle_maps", IdentifierValidator.identifier(" Puzzle_Maps ", "categoryId"));
  }

  @Test
  void rejectsUnsafeIdentifiers() {
    assertThrows(
      IllegalArgumentException.class,
      () -> IdentifierValidator.identifier("../../secret", "categoryId")
    );
  }

  @Test
  void acceptsOnlyLoopbackHttp() {
    URI uri = IdentifierValidator.loopbackHttpUri("http://127.0.0.1:25567");
    assertEquals("127.0.0.1", uri.getHost());
    assertThrows(
      IllegalArgumentException.class,
      () -> IdentifierValidator.loopbackHttpUri("https://example.com")
    );
  }

  @Test
  void rejectsDefaultToken() {
    assertThrows(
      IllegalArgumentException.class,
      () -> IdentifierValidator.token("replace-with-a-long-random-token")
    );
  }
}

