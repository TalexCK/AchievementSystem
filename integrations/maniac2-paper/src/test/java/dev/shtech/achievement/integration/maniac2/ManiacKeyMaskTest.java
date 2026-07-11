package dev.shtech.achievement.integration.maniac2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ManiacKeyMaskTest {
  @Test
  void completesOnlyAfterEveryUniqueKey() {
    int mask = 0;
    for (int key = 1; key <= 14; key++) {
      mask = ManiacKeyMask.add(mask, "kluch_" + key);
    }
    assertFalse(ManiacKeyMask.complete(mask, 2));
    mask = ManiacKeyMask.add(mask, "kluch_15");
    assertFalse(ManiacKeyMask.complete(mask, 1));
    assertTrue(ManiacKeyMask.complete(mask, 2));
  }

  @Test
  void ignoresDuplicatesAndUnrelatedModels() {
    int mask = ManiacKeyMask.add(0, "kluch_8");
    assertEquals(mask, ManiacKeyMask.add(mask, "kluch_8"));
    assertEquals(mask, ManiacKeyMask.add(mask, "key_9"));
    assertEquals(mask, ManiacKeyMask.add(mask, "kluch_16"));
  }
}
