package dev.shtech.achievement.integration.maniac2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ManiacTokenNormalizerTest {
  @Test
  void splitsCopiedStackTokensAcrossSeparateInventoryStacks() {
    Set<String> inventoryTokens = new LinkedHashSet<>();
    Set<String> copied = new LinkedHashSet<>(List.of("A", "B"));
    AtomicInteger generated = new AtomicInteger();
    Set<String> first = ManiacTokenNormalizer.normalize(
      copied,
      inventoryTokens,
      1,
      () -> "N" + generated.incrementAndGet()
    );
    Set<String> second = ManiacTokenNormalizer.normalize(
      copied,
      inventoryTokens,
      1,
      () -> "N" + generated.incrementAndGet()
    );
    assertEquals(Set.of("A"), first);
    assertEquals(Set.of("B"), second);
  }

  @Test
  void preservesEveryTokenForAnUnsplitStack() {
    Set<String> normalized = ManiacTokenNormalizer.normalize(
      new LinkedHashSet<>(List.of("A", "B")),
      new LinkedHashSet<>(),
      2,
      () -> "unused"
    );
    assertEquals(Set.of("A", "B"), normalized);
  }
}
