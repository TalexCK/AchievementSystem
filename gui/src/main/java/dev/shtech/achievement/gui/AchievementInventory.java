package dev.shtech.achievement.gui;

import dev.shtech.achievement.common.PlayerSnapshot;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

final class AchievementInventory implements InventoryHolder {
  private final UUID playerUuid;
  private final GuiPage page;
  private final int pageIndex;
  private final String categoryId;
  private final PlayerSnapshot snapshot;
  private final Map<Integer, String> categorySlots;
  private Inventory inventory;
  private boolean pending;

  AchievementInventory(
    UUID playerUuid,
    GuiPage page,
    int pageIndex,
    String categoryId,
    PlayerSnapshot snapshot,
    Map<Integer, String> categorySlots
  ) {
    this.playerUuid = playerUuid;
    this.page = page;
    this.pageIndex = pageIndex;
    this.categoryId = categoryId;
    this.snapshot = snapshot;
    this.categorySlots = Map.copyOf(categorySlots);
  }

  void attach(Inventory inventory) {
    this.inventory = inventory;
  }

  UUID playerUuid() {
    return playerUuid;
  }

  GuiPage page() {
    return page;
  }

  int pageIndex() {
    return pageIndex;
  }

  String categoryId() {
    return categoryId;
  }

  PlayerSnapshot snapshot() {
    return snapshot;
  }

  String categoryAt(int slot) {
    return categorySlots.get(slot);
  }

  boolean pending() {
    return pending;
  }

  void pending(boolean pending) {
    this.pending = pending;
  }

  @Override
  public @NotNull Inventory getInventory() {
    return Objects.requireNonNull(inventory, "inventory");
  }
}
