package dev.shtech.achievement.integration.maniac2;

import java.util.Map;

final class ManiacCollectionProgress {
  private static final Map<String, Integer> REQUIRED_ITEMS = Map.of(
    "izolenta", 0,
    "boltorez", 1,
    "otvertka", 2,
    "medicaments", 3,
    "med_box", 4
  );
  private static final int COMPLETE_ITEMS = (1 << REQUIRED_ITEMS.size()) - 1;

  private ManiacCollectionProgress() {
  }

  static int addItem(int mask, String model) {
    Integer bit = REQUIRED_ITEMS.get(model);
    return bit == null ? mask : mask | (1 << bit);
  }

  static boolean complete(
    int keyMask,
    int basementKeys,
    int fuelCans,
    int itemMask
  ) {
    return ManiacKeyMask.complete(keyMask, basementKeys)
      && fuelCans >= 6
      && (itemMask & COMPLETE_ITEMS) == COMPLETE_ITEMS;
  }
}
