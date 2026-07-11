package dev.shtech.achievement.integration.maniac2;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

final class ManiacTokenNormalizer {
  private ManiacTokenNormalizer() {
  }

  static Set<String> normalize(
    Set<String> existing,
    Set<String> inventoryTokens,
    int expected,
    Supplier<String> tokenFactory
  ) {
    Set<String> normalized = new LinkedHashSet<>();
    for (String token : existing) {
      if (normalized.size() >= expected) {
        break;
      }
      if (inventoryTokens.add(token)) {
        normalized.add(token);
      }
    }
    while (normalized.size() < expected) {
      String token = tokenFactory.get();
      if (inventoryTokens.add(token)) {
        normalized.add(token);
      }
    }
    return normalized;
  }
}
