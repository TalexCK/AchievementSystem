package dev.shtech.achievement.integration.maniac2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ManiacCollectionProgressTest {
  @Test
  void requiresEveryToolMedicalItemFuelCanAndKey() {
    int keys = 0;
    for (int key = 1; key <= 15; key++) {
      keys = ManiacKeyMask.add(keys, "kluch_" + key);
    }
    int items = 0;
    for (String model : new String[]{
      "izolenta",
      "boltorez",
      "otvertka",
      "medicaments",
      "med_box"
    }) {
      items = ManiacCollectionProgress.addItem(items, model);
    }
    assertFalse(ManiacCollectionProgress.complete(keys, 1, 6, items));
    assertFalse(ManiacCollectionProgress.complete(keys, 2, 5, items));
    assertTrue(ManiacCollectionProgress.complete(keys, 2, 6, items));
  }
}
