package dev.shtech.achievement.integration.maniac2;

import org.bukkit.NamespacedKey;

final class ManiacKeyMask {
  static final int KEY_COUNT = 15;
  static final int COMPLETE = (1 << KEY_COUNT) - 1;

  private ManiacKeyMask() {
  }

  static int add(int mask, NamespacedKey model) {
    if (model == null || !model.getNamespace().equals(NamespacedKey.MINECRAFT)) {
      return mask;
    }
    return add(mask, model.getKey());
  }

  static int add(int mask, String model) {
    if (model == null || !model.startsWith("kluch_")) {
      return mask;
    }
    try {
      int key = Integer.parseInt(model.substring("kluch_".length()));
      if (key < 1 || key > KEY_COUNT) {
        return mask;
      }
      return mask | (1 << (key - 1));
    } catch (NumberFormatException error) {
      return mask;
    }
  }

  static boolean complete(int mask, int basementKeys) {
    return (mask & COMPLETE) == COMPLETE && basementKeys >= 2;
  }
}
