package dev.shtech.achievement.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

final class AchievementHotbarEntry implements Listener {
  static final int SLOT = 4;
  private static final long RESTORE_INTERVAL_TICKS = 40L;
  private final Plugin plugin;
  private final Consumer<Player> openGui;
  private final NamespacedKey markerKey;
  private final Set<UUID> fullInventoryWarnings = new HashSet<>();
  private BukkitTask restoreTask;
  private boolean enabled;

  AchievementHotbarEntry(Plugin plugin, Consumer<Player> openGui) {
    this.plugin = plugin;
    this.openGui = openGui;
    markerKey = new NamespacedKey(plugin, "achievement_menu");
  }

  void enable() {
    enabled = true;
    Bukkit.getOnlinePlayers().forEach(player -> ensureEntry(player, true));
    restoreTask = Bukkit.getScheduler().runTaskTimer(
      plugin,
      () -> Bukkit.getOnlinePlayers().forEach(player -> ensureEntry(player, false)),
      RESTORE_INTERVAL_TICKS,
      RESTORE_INTERVAL_TICKS
    );
  }

  void disable() {
    enabled = false;
    if (restoreTask != null) {
      restoreTask.cancel();
      restoreTask = null;
    }
    Bukkit.getOnlinePlayers().forEach(this::removeEntries);
    fullInventoryWarnings.clear();
  }

  boolean ensureEntry(Player player, boolean notifyFailure) {
    PlayerInventory inventory = player.getInventory();
    ItemStack current = inventory.getItem(SLOT);
    HotbarSlotPlanner.Plan plan = HotbarSlotPlanner.plan(slotKinds(inventory), SLOT);
    if (!plan.placeable()) {
      if (notifyFailure && fullInventoryWarnings.add(player.getUniqueId())) {
        player.sendMessage(Component.text(
          "Achievement menu item could not be placed because your inventory is full.",
          NamedTextColor.RED
        ));
      }
      return false;
    }

    if (plan.relocationSlot() >= 0) {
      inventory.setItem(plan.relocationSlot(), current);
    }
    if (!isEntry(current) || current.getAmount() != 1) {
      inventory.setItem(SLOT, createEntry());
    }
    plan.duplicateEntrySlots().forEach(slot -> inventory.setItem(slot, null));
    fullInventoryWarnings.remove(player.getUniqueId());
    return true;
  }

  boolean isEntry(ItemStack item) {
    if (item == null || item.getType() != Material.DIAMOND || !item.hasItemMeta()) {
      return false;
    }
    Byte marker = item.getItemMeta().getPersistentDataContainer().get(
      markerKey,
      PersistentDataType.BYTE
    );
    return marker != null && marker == (byte) 1;
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent event) {
    scheduleEnsure(event.getPlayer());
  }

  @EventHandler
  public void onRespawn(PlayerRespawnEvent event) {
    scheduleEnsure(event.getPlayer());
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    removeEntries(event.getPlayer());
    fullInventoryWarnings.remove(event.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onDeath(PlayerDeathEvent event) {
    event.getDrops().removeIf(this::isEntry);
  }

  @EventHandler
  public void onInteract(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_AIR
      && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
      return;
    }
    if (!isEntry(event.getItem())) {
      return;
    }
    event.setCancelled(true);
    openGui.accept(event.getPlayer());
  }

  @EventHandler
  public void onDrop(PlayerDropItemEvent event) {
    if (!isEntry(event.getItemDrop().getItemStack())) {
      return;
    }
    event.setCancelled(true);
    scheduleEnsure(event.getPlayer());
  }

  @EventHandler
  public void onSwapHands(PlayerSwapHandItemsEvent event) {
    if (isEntry(event.getMainHandItem()) || isEntry(event.getOffHandItem())) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    boolean protectedSlot = event.getClickedInventory() == player.getInventory()
      && event.getSlot() == SLOT
      && isEntry(player.getInventory().getItem(SLOT));
    boolean protectedHotbarSwap = event.getHotbarButton() == SLOT
      && isEntry(player.getInventory().getItem(SLOT));
    if (protectedSlot
      || protectedHotbarSwap
      || isEntry(event.getCurrentItem())
      || isEntry(event.getCursor())) {
      event.setCancelled(true);
      scheduleEnsure(player);
    }
  }

  @EventHandler
  public void onInventoryDrag(InventoryDragEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) {
      return;
    }
    boolean targetsSlot = event.getRawSlots().stream().anyMatch(rawSlot ->
      event.getView().getInventory(rawSlot) == player.getInventory()
        && event.getView().convertSlot(rawSlot) == SLOT
    );
    if (isEntry(event.getOldCursor())
      || targetsSlot && isEntry(player.getInventory().getItem(SLOT))) {
      event.setCancelled(true);
      scheduleEnsure(player);
    }
  }

  private void scheduleEnsure(Player player) {
    if (!enabled) {
      return;
    }
    Bukkit.getScheduler().runTask(plugin, () -> {
      if (enabled && player.isOnline()) {
        ensureEntry(player, true);
      }
    });
  }

  private ItemStack createEntry() {
    ItemStack item = new ItemStack(Material.DIAMOND);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(
      Component.text("成就图鉴", NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
    );
    meta.lore(List.of(
      Component.text("右键打开成就页面", NamedTextColor.YELLOW),
      Component.text("查看进度、奖励与展示徽章", NamedTextColor.GRAY)
    ));
    meta.setEnchantmentGlintOverride(true);
    meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
    item.setItemMeta(meta);
    return item;
  }

  private List<HotbarSlotPlanner.SlotKind> slotKinds(PlayerInventory inventory) {
    ItemStack[] storage = inventory.getStorageContents();
    List<HotbarSlotPlanner.SlotKind> kinds = new ArrayList<>(storage.length);
    for (int slot = 0; slot < storage.length; slot++) {
      HotbarSlotPlanner.SlotKind kind = isEntry(storage[slot])
        ? HotbarSlotPlanner.SlotKind.ENTRY
        : isEmpty(storage[slot])
          ? HotbarSlotPlanner.SlotKind.EMPTY
          : HotbarSlotPlanner.SlotKind.OTHER;
      kinds.add(kind);
    }
    return kinds;
  }

  private void removeDuplicateEntries(PlayerInventory inventory, int retainedSlot) {
    ItemStack[] contents = inventory.getContents();
    for (int slot = 0; slot < contents.length; slot++) {
      if (slot != retainedSlot && isEntry(contents[slot])) {
        inventory.setItem(slot, null);
      }
    }
  }

  private void removeEntries(Player player) {
    PlayerInventory inventory = player.getInventory();
    removeDuplicateEntries(inventory, -1);
    if (isEntry(player.getItemOnCursor())) {
      player.setItemOnCursor(null);
    }
  }

  private static boolean isEmpty(ItemStack item) {
    return item == null || item.getType().isAir();
  }
}
