package dev.shtech.achievement.gui;

import java.util.ArrayList;
import java.util.List;

final class HotbarSlotPlanner {
  private HotbarSlotPlanner() {
  }

  static Plan plan(List<SlotKind> slots, int targetSlot) {
    if (targetSlot < 0 || targetSlot >= slots.size()) {
      throw new IllegalArgumentException("Target slot is outside the inventory.");
    }
    SlotKind target = slots.get(targetSlot);
    int relocationSlot = -1;
    if (target == SlotKind.OTHER) {
      relocationSlot = firstSlot(slots, targetSlot, SlotKind.ENTRY);
      if (relocationSlot < 0) {
        relocationSlot = firstSlot(slots, targetSlot, SlotKind.EMPTY);
      }
      if (relocationSlot < 0) {
        return new Plan(false, -1, List.of());
      }
    }
    List<Integer> duplicateEntries = new ArrayList<>();
    for (int slot = 0; slot < slots.size(); slot++) {
      if (slot != targetSlot
        && slot != relocationSlot
        && slots.get(slot) == SlotKind.ENTRY) {
        duplicateEntries.add(slot);
      }
    }
    return new Plan(true, relocationSlot, List.copyOf(duplicateEntries));
  }

  private static int firstSlot(List<SlotKind> slots, int targetSlot, SlotKind kind) {
    for (int slot = 0; slot < slots.size(); slot++) {
      if (slot != targetSlot && slots.get(slot) == kind) {
        return slot;
      }
    }
    return -1;
  }

  enum SlotKind {
    EMPTY,
    ENTRY,
    OTHER
  }

  record Plan(boolean placeable, int relocationSlot, List<Integer> duplicateEntrySlots) {
  }
}
