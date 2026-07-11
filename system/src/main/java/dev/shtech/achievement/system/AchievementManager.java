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
import java.util.concurrent.ConcurrentHashMap;

public final class AchievementManager {
  private final AchievementDatabase database;
  private final int maximumSelectedBadges;
  private final Runnable rewardsWakeup;
  private final ConcurrentHashMap<UUID, Object> selectionLocks = new ConcurrentHashMap<>();
  private volatile AchievementCatalog catalog;

  public AchievementManager(
    AchievementDatabase database,
    AchievementCatalog catalog,
    int maximumSelectedBadges,
    Runnable rewardsWakeup
  ) {
    this.database = database;
    this.catalog = catalog;
    this.maximumSelectedBadges = maximumSelectedBadges;
    this.rewardsWakeup = rewardsWakeup;
  }

  public ProgressResponse progress(ProgressRequest rawRequest) throws SQLException {
    ProgressRequest request = rawRequest.validated();
    AchievementCategory category = catalog.require(request.categoryId());
    ProgressResult result = database.applyProgress(request, category);
    if (!result.newlyUnlockedTiers().isEmpty()) {
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
    }
    rewardsWakeup.run();
    return toSnapshot(state, currentCatalog, unlockStatistics);
  }

  public BadgeSelectionResponse select(
    UUID playerUuid,
    BadgeSelectionRequest rawRequest
  ) throws SQLException {
    synchronized (selectionLocks.computeIfAbsent(playerUuid, ignored -> new Object())) {
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
    return new BadgeSelectionResponse(true, badges, null);
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
}
