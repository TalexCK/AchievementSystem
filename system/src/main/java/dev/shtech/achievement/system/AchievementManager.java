package dev.shtech.achievement.system;

import dev.shtech.achievement.common.BadgeSelectionRequest;
import dev.shtech.achievement.common.BadgeSelectionResponse;
import dev.shtech.achievement.common.BadgeSnapshot;
import dev.shtech.achievement.common.CategorySnapshot;
import dev.shtech.achievement.common.IdentifierValidator;
import dev.shtech.achievement.common.PlayerSnapshot;
import dev.shtech.achievement.common.ProgressRequest;
import dev.shtech.achievement.common.ProgressResponse;
import dev.shtech.achievement.common.TierSnapshot;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class AchievementManager {
  private final AchievementDatabase database;
  private final LuckPermsBadgeService badgeService;
  private final int maximumSelectedBadges;
  private final Consumer<String> errorLogger;
  private final Runnable rewardsWakeup;
  private final ConcurrentHashMap<UUID, Object> suffixLocks = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<UUID, CompletableFuture<Void>> suffixUpdates =
    new ConcurrentHashMap<>();
  private volatile AchievementCatalog catalog;

  public AchievementManager(
    AchievementDatabase database,
    AchievementCatalog catalog,
    LuckPermsBadgeService badgeService,
    int maximumSelectedBadges,
    Consumer<String> errorLogger,
    Runnable rewardsWakeup
  ) {
    this.database = database;
    this.catalog = catalog;
    this.badgeService = badgeService;
    this.maximumSelectedBadges = maximumSelectedBadges;
    this.errorLogger = errorLogger;
    this.rewardsWakeup = rewardsWakeup;
  }

  public ProgressResponse progress(ProgressRequest rawRequest) throws SQLException {
    ProgressRequest request = rawRequest.validated();
    AchievementCategory category = catalog.require(request.categoryId());
    ProgressResult result = database.applyProgress(request, category);
    UUID playerUuid = UUID.fromString(request.playerUuid());
    if (!result.newlyUnlockedTiers().isEmpty()) {
      refreshSuffix(playerUuid, request.playerName());
      rewardsWakeup.run();
    }
    return new ProgressResponse(
      true,
      result.duplicate(),
      result.progress(),
      result.currentTier(),
      result.newlyUnlockedTiers(),
      null
    );
  }

  public PlayerSnapshot snapshot(UUID playerUuid, String rawPlayerName) throws SQLException {
    String playerName = IdentifierValidator.playerName(rawPlayerName);
    AchievementCatalog currentCatalog = catalog;
    PlayerState state = database.loadPlayer(playerUuid, playerName, currentCatalog);
    Map<String, Map<Integer, TierUnlockStatistics>> unlockStatistics =
      database.unlockStatistics();
    List<String> validSelections = validSelections(state, currentCatalog);
    if (!validSelections.equals(state.selectedCategories())) {
      database.replaceSelections(playerUuid, validSelections);
      state = new PlayerState(playerUuid, playerName, state.progress(), validSelections);
      refreshSuffix(playerUuid, playerName);
    }
    rewardsWakeup.run();
    return toSnapshot(state, currentCatalog, unlockStatistics);
  }

  public BadgeSelectionResponse select(
    UUID playerUuid,
    BadgeSelectionRequest rawRequest
  ) throws SQLException {
    synchronized (suffixLocks.computeIfAbsent(playerUuid, ignored -> new Object())) {
      return selectLocked(playerUuid, rawRequest);
    }
  }

  private BadgeSelectionResponse selectLocked(
    UUID playerUuid,
    BadgeSelectionRequest rawRequest
  ) throws SQLException {
    BadgeSelectionRequest request = rawRequest.validated(maximumSelectedBadges);
    String playerName = database.playerName(playerUuid);
    PlayerState state = database.loadPlayer(playerUuid, playerName, catalog);
    for (String categoryId : request.categoryIds()) {
      AchievementCategory category = catalog.require(categoryId);
      long progress = state.progress().getOrDefault(categoryId, 0L);
      if (!category.badgeEnabled() || category.currentTier(progress) < 1) {
        throw new IllegalArgumentException("Only unlocked achievement badges can be selected.");
      }
    }
    database.replaceSelections(playerUuid, request.categoryIds());
    PlayerState updated = new PlayerState(
      playerUuid,
      state.playerName(),
      state.progress(),
      request.categoryIds()
    );
    List<BadgeSnapshot> badges = badges(updated, catalog);
    queueSuffix(playerUuid, badges);
    return new BadgeSelectionResponse(true, badges, null);
  }

  public void refreshSuffix(UUID playerUuid, String playerName) {
    synchronized (suffixLocks.computeIfAbsent(playerUuid, ignored -> new Object())) {
      try {
        AchievementCatalog currentCatalog = catalog;
        PlayerState state = database.loadPlayer(playerUuid, playerName, currentCatalog);
        queueSuffix(playerUuid, badges(state, currentCatalog));
      } catch (SQLException error) {
        errorLogger.accept("Failed to refresh achievement suffix: " + error.getMessage());
      }
    }
  }

  public void reload(AchievementCatalog catalog) {
    this.catalog = catalog;
  }

  public AchievementCatalog catalog() {
    return catalog;
  }

  public AdminAchievementResult grant(
    String target,
    String categoryId,
    int tierLevel
  ) throws SQLException {
    AchievementPlayer player = database.requirePlayer(target);
    AchievementCategory category = catalog.require(categoryId);
    int resolvedTier = tierLevel <= 0 ? category.tiers().size() : tierLevel;
    AchievementTier tier = category.tier(resolvedTier);
    ProgressResponse response = progress(new ProgressRequest(
      player.uuid().toString(),
      player.name(),
      category.id(),
      dev.shtech.achievement.common.ProgressOperation.MAX,
      tier.threshold(),
      "admin:" + UUID.randomUUID(),
      "achievement_admin"
    ));
    return new AdminAchievementResult(
      player.uuid(),
      player.name(),
      category.id(),
      response.progress(),
      response.currentTier()
    );
  }

  public AdminAchievementResult revoke(
    String target,
    String categoryId,
    int tierLevel
  ) throws SQLException {
    AchievementPlayer player = database.requirePlayer(target);
    AchievementCategory category = catalog.require(categoryId);
    ProgressResult result = database.revoke(player, category, tierLevel);
    refreshSuffix(player.uuid(), player.name());
    return new AdminAchievementResult(
      player.uuid(),
      player.name(),
      category.id(),
      result.progress(),
      result.currentTier()
    );
  }

  public List<AdminAchievementResult> view(
    String target,
    String categoryId
  ) throws SQLException {
    AchievementPlayer player = database.requirePlayer(target);
    PlayerState state = database.loadPlayer(player.uuid(), player.name(), catalog);
    List<AchievementCategory> categories = categoryId == null || categoryId.isBlank()
      ? catalog.categories()
      : List.of(catalog.require(categoryId));
    List<AdminAchievementResult> result = new ArrayList<>();
    for (AchievementCategory category : categories) {
      long progress = state.progress().getOrDefault(category.id(), 0L);
      result.add(new AdminAchievementResult(
        player.uuid(),
        player.name(),
        category.id(),
        progress,
        category.currentTier(progress)
      ));
    }
    return result;
  }

  private void queueSuffix(UUID playerUuid, List<BadgeSnapshot> badges) {
    List<BadgeSnapshot> snapshot = List.copyOf(badges);
    suffixUpdates.compute(playerUuid, (ignored, previous) -> {
      CompletableFuture<Void> ready = previous == null
        ? CompletableFuture.completedFuture(null)
        : previous.handle((value, error) -> null);
      return ready.thenCompose(value -> badgeService.apply(playerUuid, snapshot))
        .exceptionally(error -> {
          errorLogger.accept("Failed to update LuckPerms achievement suffix: " + rootMessage(error));
          return null;
        });
    });
  }

  private List<String> validSelections(PlayerState state, AchievementCatalog currentCatalog) {
    List<String> valid = new ArrayList<>();
    for (String categoryId : state.selectedCategories()) {
      AchievementCategory category = currentCatalog.find(categoryId).orElse(null);
      if (category != null
        && category.badgeEnabled()
        && category.currentTier(state.progress().getOrDefault(categoryId, 0L)) > 0
        && valid.size() < maximumSelectedBadges) {
        valid.add(categoryId);
      }
    }
    return valid;
  }

  private PlayerSnapshot toSnapshot(
    PlayerState state,
    AchievementCatalog currentCatalog,
    Map<String, Map<Integer, TierUnlockStatistics>> unlockStatistics
  ) {
    Set<String> selected = new HashSet<>(state.selectedCategories());
    List<CategorySnapshot> categories = new ArrayList<>();
    int unlockedTiers = 0;
    int totalTiers = 0;
    for (AchievementCategory category : currentCatalog.categories()) {
      long progress = state.progress().getOrDefault(category.id(), 0L);
      int currentTier = category.currentTier(progress);
      boolean discovered = !category.hidden() || currentTier > 0;
      Map<Integer, TierUnlockStatistics> categoryStatistics =
        unlockStatistics.getOrDefault(category.id(), Map.of());
      unlockedTiers += currentTier;
      totalTiers += category.tiers().size();
      List<TierSnapshot> tiers = category.tiers().stream()
        .map(tier -> {
          TierUnlockStatistics statistics = categoryStatistics.getOrDefault(
            tier.level(),
            new TierUnlockStatistics(0, null)
          );
          return new TierSnapshot(
            tier.level(),
            discovered ? tier.name() : "???",
            tier.threshold(),
            discovered ? tier.color() : "#555555",
            progress >= tier.threshold(),
            statistics.unlockedPlayers(),
            category.hidden() ? statistics.firstUnlockerName() : null,
            discovered
              ? tier.rewards().stream().map(RewardDefinition::description).toList()
              : List.of()
          );
        })
        .toList();
      categories.add(new CategorySnapshot(
        category.id(),
        category.hidden(),
        discovered,
        category.badgeEnabled(),
        discovered ? category.displayName() : "隐藏成就",
        discovered ? category.description() : List.of("完成后揭晓"),
        discovered ? category.material() : "GRAY_STAINED_GLASS_PANE",
        discovered ? category.symbol() : "?",
        progress,
        currentTier,
        category.nextThreshold(progress),
        categoryStatistics.getOrDefault(1, new TierUnlockStatistics(0, null))
          .unlockedPlayers(),
        selected.contains(category.id()),
        tiers
      ));
    }
    return new PlayerSnapshot(
      state.playerUuid().toString(),
      state.playerName(),
      unlockedTiers,
      totalTiers,
      badges(state, currentCatalog),
      categories
    );
  }

  private List<BadgeSnapshot> badges(PlayerState state, AchievementCatalog currentCatalog) {
    List<BadgeSnapshot> badges = new ArrayList<>();
    for (int slot = 0; slot < state.selectedCategories().size(); slot++) {
      String categoryId = state.selectedCategories().get(slot);
      AchievementCategory category = currentCatalog.find(categoryId).orElse(null);
      if (category == null) {
        continue;
      }
      if (!category.badgeEnabled()) {
        continue;
      }
      int tierLevel = category.currentTier(state.progress().getOrDefault(categoryId, 0L));
      if (tierLevel < 1) {
        continue;
      }
      AchievementTier tier = category.tier(tierLevel);
      badges.add(new BadgeSnapshot(
        slot + 1,
        category.id(),
        category.displayName(),
        category.symbol(),
        tier.color(),
        tierLevel
      ));
    }
    return badges;
  }

  private static String rootMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }
}
