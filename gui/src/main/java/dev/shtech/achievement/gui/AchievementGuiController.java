package dev.shtech.achievement.gui;

import dev.shtech.achievement.common.AchievementApiClient;
import dev.shtech.achievement.common.BadgeSelectionRequest;
import dev.shtech.achievement.common.BadgeSelectionResponse;
import dev.shtech.achievement.common.BadgeSnapshot;
import dev.shtech.achievement.common.CategorySnapshot;
import dev.shtech.achievement.common.PlayerSnapshot;
import dev.shtech.achievement.common.TierSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class AchievementGuiController implements Listener {
  private static final int INVENTORY_SIZE = 54;
  private static final int PAGE_SIZE = 28;
  private static final int PREVIOUS_SLOT = 45;
  private static final int CLOSE_OR_BACK_SLOT = 48;
  private static final int BADGE_SLOT = 49;
  private static final int REFRESH_SLOT = 50;
  private static final int NEXT_SLOT = 53;
  private static final int[] CONTENT_SLOTS = {
    10, 11, 12, 13, 14, 15, 16,
    19, 20, 21, 22, 23, 24, 25,
    28, 29, 30, 31, 32, 33, 34,
    37, 38, 39, 40, 41, 42, 43
  };
  private static final int[] TIER_SLOTS = {
    10, 11, 12, 13, 14, 15, 16, 21, 22, 23
  };
  private static final int[] PROGRESS_SLOTS = {37, 38, 39, 40, 41, 42, 43};
  private final JavaPlugin plugin;
  private final AchievementApiClient client;
  private final TabBadgeSynchronizer badgeSynchronizer;
  private volatile boolean enabled = true;

  AchievementGuiController(
    JavaPlugin plugin,
    AchievementApiClient client,
    TabBadgeSynchronizer badgeSynchronizer
  ) {
    this.plugin = plugin;
    this.client = client;
    this.badgeSynchronizer = badgeSynchronizer;
  }

  void open(Player player) {
    player.sendMessage(Component.text("正在加载成就…", NamedTextColor.AQUA));
    requestSnapshot(player, snapshot -> renderMain(player, snapshot, 0), () -> {
    });
  }

  void disable() {
    enabled = false;
  }

  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    if (!(event.getView().getTopInventory().getHolder() instanceof AchievementInventory holder)) {
      return;
    }
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)
      || !player.getUniqueId().equals(holder.playerUuid())
      || event.getRawSlot() < 0
      || event.getRawSlot() >= INVENTORY_SIZE
      || holder.pending()) {
      return;
    }
    int slot = event.getRawSlot();
    if (slot == CLOSE_OR_BACK_SLOT) {
      if (holder.page() == GuiPage.MAIN) {
        player.closeInventory();
      } else {
        renderMain(player, holder.snapshot(), holder.pageIndex());
      }
      return;
    }
    if (slot == REFRESH_SLOT) {
      refresh(player, holder);
      return;
    }
    switch (holder.page()) {
      case MAIN -> handleMainClick(player, holder, slot);
      case CATEGORY -> handleCategoryClick(player, holder, slot);
      case BADGES -> handleBadgeClick(player, holder, slot);
    }
  }

  @EventHandler
  public void onInventoryDrag(InventoryDragEvent event) {
    if (!(event.getView().getTopInventory().getHolder() instanceof AchievementInventory)) {
      return;
    }
    if (event.getRawSlots().stream().anyMatch(slot -> slot < INVENTORY_SIZE)) {
      event.setCancelled(true);
    }
  }

  private void handleMainClick(Player player, AchievementInventory holder, int slot) {
    if (slot == PREVIOUS_SLOT && holder.pageIndex() > 0) {
      renderMain(player, holder.snapshot(), holder.pageIndex() - 1);
      return;
    }
    int pageCount = Pagination.pageCount(holder.snapshot().categories().size(), PAGE_SIZE);
    if (slot == NEXT_SLOT && holder.pageIndex() + 1 < pageCount) {
      renderMain(player, holder.snapshot(), holder.pageIndex() + 1);
      return;
    }
    if (slot == BADGE_SLOT) {
      renderBadges(player, holder.snapshot(), 0);
      return;
    }
    String categoryId = holder.categoryAt(slot);
    if (categoryId == null) {
      return;
    }
    CategorySnapshot category = category(holder.snapshot(), categoryId);
    if (category == null) {
      player.sendMessage(Component.text("Achievement category was not found.", NamedTextColor.RED));
      return;
    }
    renderCategory(player, holder.snapshot(), category, holder.pageIndex());
  }

  private void handleCategoryClick(Player player, AchievementInventory holder, int slot) {
    if (slot == BADGE_SLOT) {
      renderBadges(player, holder.snapshot(), 0);
    }
  }

  private void handleBadgeClick(Player player, AchievementInventory holder, int slot) {
    List<CategorySnapshot> unlocked = unlockedCategories(holder.snapshot());
    int pageCount = Pagination.pageCount(unlocked.size(), PAGE_SIZE);
    if (slot == PREVIOUS_SLOT && holder.pageIndex() > 0) {
      renderBadges(player, holder.snapshot(), holder.pageIndex() - 1);
      return;
    }
    if (slot == NEXT_SLOT && holder.pageIndex() + 1 < pageCount) {
      renderBadges(player, holder.snapshot(), holder.pageIndex() + 1);
      return;
    }
    String categoryId = holder.categoryAt(slot);
    if (categoryId != null) {
      updateBadgeSelection(player, holder, slot, categoryId);
    }
  }

  private void updateBadgeSelection(
    Player player,
    AchievementInventory holder,
    int slot,
    String categoryId
  ) {
    CategorySnapshot category = category(holder.snapshot(), categoryId);
    if (category == null || category.currentTier() <= 0) {
      player.sendMessage(Component.text("Achievement is not unlocked.", NamedTextColor.RED));
      return;
    }
    List<String> selected = holder.snapshot().selectedBadges().stream()
      .sorted(Comparator.comparingInt(BadgeSnapshot::slot))
      .map(BadgeSnapshot::categoryId)
      .filter(Objects::nonNull)
      .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    if (selected.remove(categoryId)) {
      submitBadges(player, holder, slot, selected);
      return;
    }
    if (selected.size() >= 3) {
      player.sendMessage(Component.text("You can select up to 3 badges.", NamedTextColor.RED));
      return;
    }
    selected.add(categoryId);
    submitBadges(player, holder, slot, selected);
  }

  private void submitBadges(
    Player player,
    AchievementInventory holder,
    int slot,
    List<String> selected
  ) {
    BadgeSelectionRequest request;
    try {
      request = new BadgeSelectionRequest(selected).validated(3);
    } catch (IllegalArgumentException error) {
      player.sendMessage(Component.text("Badge selection is invalid.", NamedTextColor.RED));
      return;
    }
    holder.pending(true);
    holder.getInventory().setItem(slot, GuiItems.pending());
    client.selectBadges(player.getUniqueId(), request).whenComplete((response, error) ->
      runSync(() -> completeBadgeSelection(player, holder, response, error))
    );
  }

  private void completeBadgeSelection(
    Player player,
    AchievementInventory holder,
    BadgeSelectionResponse response,
    Throwable error
  ) {
    if (!player.isOnline() || !isViewing(player, holder)) {
      return;
    }
    if (error != null || response == null || !response.accepted()) {
      holder.pending(false);
      renderBadges(player, holder.snapshot(), holder.pageIndex());
      player.sendMessage(Component.text("Badge selection could not be saved.", NamedTextColor.RED));
      logFailure("Failed to save badge selection for " + player.getName(), error);
      return;
    }
    requestSnapshot(
      player,
      snapshot -> {
        if (isViewing(player, holder)) {
          renderBadges(player, snapshot, holder.pageIndex());
          player.sendMessage(Component.text("展示徽章已更新。", NamedTextColor.GREEN));
        }
      },
      () -> holder.pending(false)
    );
  }

  private void refresh(Player player, AchievementInventory holder) {
    player.sendMessage(Component.text("正在刷新成就…", NamedTextColor.AQUA));
    requestSnapshot(player, snapshot -> {
      switch (holder.page()) {
        case MAIN -> renderMain(player, snapshot, holder.pageIndex());
        case BADGES -> renderBadges(player, snapshot, holder.pageIndex());
        case CATEGORY -> {
          CategorySnapshot category = category(snapshot, holder.categoryId());
          if (category == null) {
            renderMain(player, snapshot, holder.pageIndex());
          } else {
            renderCategory(player, snapshot, category, holder.pageIndex());
          }
        }
      }
    }, () -> {
    });
  }

  private void renderMain(Player player, PlayerSnapshot snapshot, int requestedPage) {
    List<CategorySnapshot> categories = snapshot.categories();
    int page = Pagination.clampPage(requestedPage, categories.size(), PAGE_SIZE);
    int pageCount = Pagination.pageCount(categories.size(), PAGE_SIZE);
    List<CategorySnapshot> visible = Pagination.slice(categories, page, PAGE_SIZE);
    Map<Integer, String> categorySlots = new HashMap<>();
    for (int index = 0; index < visible.size(); index++) {
      categorySlots.put(CONTENT_SLOTS[index], visible.get(index).id());
    }
    AchievementInventory holder = new AchievementInventory(
      player.getUniqueId(),
      GuiPage.MAIN,
      page,
      null,
      snapshot,
      categorySlots
    );
    Inventory inventory = createInventory(holder, "成就总览 · " + (page + 1) + "/" + pageCount);
    inventory.setItem(4, GuiItems.playerSummary(player, snapshot));
    for (int index = 0; index < visible.size(); index++) {
      inventory.setItem(CONTENT_SLOTS[index], GuiItems.category(visible.get(index)));
    }
    inventory.setItem(CLOSE_OR_BACK_SLOT, GuiItems.close());
    inventory.setItem(BADGE_SLOT, GuiItems.badges());
    inventory.setItem(REFRESH_SLOT, GuiItems.refresh());
    if (page > 0) {
      inventory.setItem(PREVIOUS_SLOT, GuiItems.previous(page));
    }
    if (page + 1 < pageCount) {
      inventory.setItem(NEXT_SLOT, GuiItems.next(page, pageCount));
    }
    player.openInventory(inventory);
  }

  private void renderCategory(
    Player player,
    PlayerSnapshot snapshot,
    CategorySnapshot category,
    int returnPage
  ) {
    AchievementInventory holder = new AchievementInventory(
      player.getUniqueId(),
      GuiPage.CATEGORY,
      returnPage,
      category.id(),
      snapshot,
      Map.of()
    );
    Inventory inventory = createInventory(holder, "成就详情 · " + category.displayName());
    inventory.setItem(4, GuiItems.categoryHeader(category));
    Map<Integer, TierSnapshot> tiers = new LinkedHashMap<>();
    category.tiers().stream()
      .sorted(Comparator.comparingInt(TierSnapshot::level))
      .limit(10)
      .forEach(tier -> tiers.put(tier.level(), tier));
    for (int level = 1; level <= 10; level++) {
      TierSnapshot tier = tiers.get(level);
      ItemStack item = tier == null
        ? GuiItems.missingTier(level)
        : GuiItems.tier(tier, level == category.currentTier(), category.hidden());
      inventory.setItem(TIER_SLOTS[level - 1], item);
    }
    long target = category.tiers().stream()
      .mapToLong(TierSnapshot::threshold)
      .max()
      .orElse(0L);
    int filled = ProgressBar.filledSegments(Math.max(0L, category.progress()), target, PROGRESS_SLOTS.length);
    for (int index = 0; index < PROGRESS_SLOTS.length; index++) {
      inventory.setItem(
        PROGRESS_SLOTS[index],
        GuiItems.progressSegment(category.progress(), target, index < filled)
      );
    }
    inventory.setItem(CLOSE_OR_BACK_SLOT, GuiItems.back());
    inventory.setItem(BADGE_SLOT, GuiItems.badges());
    inventory.setItem(REFRESH_SLOT, GuiItems.refresh());
    player.openInventory(inventory);
  }

  private void renderBadges(Player player, PlayerSnapshot snapshot, int requestedPage) {
    List<CategorySnapshot> categories = unlockedCategories(snapshot);
    int page = Pagination.clampPage(requestedPage, categories.size(), PAGE_SIZE);
    int pageCount = Pagination.pageCount(categories.size(), PAGE_SIZE);
    List<CategorySnapshot> visible = Pagination.slice(categories, page, PAGE_SIZE);
    Map<Integer, String> categorySlots = new HashMap<>();
    for (int index = 0; index < visible.size(); index++) {
      categorySlots.put(CONTENT_SLOTS[index], visible.get(index).id());
    }
    AchievementInventory holder = new AchievementInventory(
      player.getUniqueId(),
      GuiPage.BADGES,
      page,
      null,
      snapshot,
      categorySlots
    );
    Inventory inventory = createInventory(holder, "徽章展示 · " + (page + 1) + "/" + pageCount);
    Map<String, Integer> selectedSlots = new HashMap<>();
    snapshot.selectedBadges().forEach(badge -> selectedSlots.put(badge.categoryId(), badge.slot()));
    for (int index = 0; index < visible.size(); index++) {
      CategorySnapshot category = visible.get(index);
      inventory.setItem(
        CONTENT_SLOTS[index],
        GuiItems.badge(category, selectedSlots.getOrDefault(category.id(), 0))
      );
    }
    inventory.setItem(CLOSE_OR_BACK_SLOT, GuiItems.back());
    inventory.setItem(BADGE_SLOT, GuiItems.badgeSummary(snapshot.selectedBadges()));
    inventory.setItem(REFRESH_SLOT, GuiItems.refresh());
    if (page > 0) {
      inventory.setItem(PREVIOUS_SLOT, GuiItems.previous(page));
    }
    if (page + 1 < pageCount) {
      inventory.setItem(NEXT_SLOT, GuiItems.next(page, pageCount));
    }
    player.openInventory(inventory);
  }

  private Inventory createInventory(AchievementInventory holder, String title) {
    Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, Component.text(title));
    holder.attach(inventory);
    for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
      if (isBorder(slot)) {
        inventory.setItem(slot, GuiItems.border((slot + slot / 9) % 2 == 0));
      }
    }
    return inventory;
  }

  private void requestSnapshot(
    Player player,
    Consumer<PlayerSnapshot> success,
    Runnable failure
  ) {
    client.player(player.getUniqueId(), player.getName()).whenComplete((snapshot, error) ->
      runSync(() -> {
        if (!player.isOnline()) {
          return;
        }
        if (error != null || snapshot == null) {
          player.sendMessage(Component.text("Achievement service is unavailable.", NamedTextColor.RED));
          logFailure("Failed to load achievement snapshot for " + player.getName(), error);
          failure.run();
          return;
        }
        badgeSynchronizer.applySnapshot(player, snapshot);
        success.accept(snapshot);
      })
    );
  }

  private void runSync(Runnable task) {
    if (!enabled || !plugin.isEnabled()) {
      return;
    }
    Bukkit.getScheduler().runTask(plugin, task);
  }

  private boolean isViewing(Player player, AchievementInventory holder) {
    return player.getOpenInventory().getTopInventory().getHolder() == holder;
  }

  private void logFailure(String message, Throwable error) {
    if (error == null) {
      plugin.getLogger().warning(message + ".");
      return;
    }
    Throwable cause = error instanceof CompletionException && error.getCause() != null
      ? error.getCause()
      : error;
    plugin.getLogger().log(Level.WARNING, message + ": " + cause.getMessage(), cause);
  }

  private static CategorySnapshot category(PlayerSnapshot snapshot, String categoryId) {
    if (categoryId == null) {
      return null;
    }
    return snapshot.categories().stream()
      .filter(category -> category.id().equals(categoryId))
      .findFirst()
      .orElse(null);
  }

  private static List<CategorySnapshot> unlockedCategories(PlayerSnapshot snapshot) {
    return snapshot.categories().stream()
      .filter(category -> category.badgeEnabled() && category.currentTier() > 0)
      .toList();
  }

  private static boolean isBorder(int slot) {
    return slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8;
  }
}
