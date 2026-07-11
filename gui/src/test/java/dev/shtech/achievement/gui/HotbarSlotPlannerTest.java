package dev.shtech.achievement.gui;

import static dev.shtech.achievement.gui.HotbarSlotPlanner.SlotKind.EMPTY;
import static dev.shtech.achievement.gui.HotbarSlotPlanner.SlotKind.ENTRY;
import static dev.shtech.achievement.gui.HotbarSlotPlanner.SlotKind.OTHER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HotbarSlotPlannerTest {
  @Test
  void preservesTargetItemByReusingAnExistingEntrySlot() {
    List<HotbarSlotPlanner.SlotKind> slots = slots(EMPTY);
    slots.set(4, OTHER);
    slots.set(8, ENTRY);
    slots.set(9, ENTRY);

    HotbarSlotPlanner.Plan plan = HotbarSlotPlanner.plan(slots, 4);

    assertTrue(plan.placeable());
    assertEquals(8, plan.relocationSlot());
    assertEquals(List.of(9), plan.duplicateEntrySlots());
  }

  @Test
  void movesTargetItemToFirstEmptySlotWhenNoEntryExists() {
    List<HotbarSlotPlanner.SlotKind> slots = slots(OTHER);
    slots.set(4, OTHER);
    slots.set(12, EMPTY);

    HotbarSlotPlanner.Plan plan = HotbarSlotPlanner.plan(slots, 4);

    assertTrue(plan.placeable());
    assertEquals(12, plan.relocationSlot());
  }

  @Test
  void refusesToOverwriteAFullInventory() {
    List<HotbarSlotPlanner.SlotKind> slots = slots(OTHER);

    HotbarSlotPlanner.Plan plan = HotbarSlotPlanner.plan(slots, 4);

    assertFalse(plan.placeable());
    assertEquals(-1, plan.relocationSlot());
  }

  @Test
  void keepsTargetEntryAndRemovesOnlyDuplicates() {
    List<HotbarSlotPlanner.SlotKind> slots = slots(OTHER);
    slots.set(4, ENTRY);
    slots.set(10, ENTRY);
    slots.set(11, ENTRY);

    HotbarSlotPlanner.Plan plan = HotbarSlotPlanner.plan(slots, 4);

    assertTrue(plan.placeable());
    assertEquals(-1, plan.relocationSlot());
    assertEquals(List.of(10, 11), plan.duplicateEntrySlots());
  }

  @Test
  void rejectsInvalidTargetSlot() {
    assertThrows(
      IllegalArgumentException.class,
      () -> HotbarSlotPlanner.plan(slots(EMPTY), 36)
    );
  }

  private static List<HotbarSlotPlanner.SlotKind> slots(
    HotbarSlotPlanner.SlotKind initial
  ) {
    return new ArrayList<>(java.util.Collections.nCopies(36, initial));
  }
}
