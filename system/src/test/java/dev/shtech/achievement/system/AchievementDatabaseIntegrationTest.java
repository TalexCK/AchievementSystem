package dev.shtech.achievement.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.shtech.achievement.common.ProgressOperation;
import dev.shtech.achievement.common.ProgressRequest;
import dev.shtech.achievement.common.PlayerSnapshot;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ACHIEVEMENT_TEST_DATABASE_URL", matches = ".+")
class AchievementDatabaseIntegrationTest {
  @Test
  void migratesPersistsUnlocksAndDeduplicatesEvents() throws Exception {
    SystemConfig config = config(System.getenv("ACHIEVEMENT_TEST_DATABASE_URL"));
    AchievementCategory category = new AchievementCategory(
      "puzzle_maps",
      1,
      false,
      true,
      "谜境探索",
      List.of(),
      "AMETHYST_SHARD",
      "◆",
      List.of(
        new AchievementTier(
          1,
          "Tier 1",
          1,
          "#AAAAAA",
          List.of(new RewardDefinition(RewardType.PERMISSION, "achievement.tier.1", "Tier 1"))
        ),
        new AchievementTier(
          2,
          "Tier 2",
          3,
          "#AA55FF",
          List.of(new RewardDefinition(RewardType.PERMISSION, "achievement.tier.2", "Tier 2"))
        )
      )
    );
    UUID playerUuid = UUID.randomUUID();
    try (AchievementDatabase database = new AchievementDatabase(config)) {
      ProgressRequest request = new ProgressRequest(
        playerUuid.toString(),
        "DatabaseTest",
        category.id(),
        ProgressOperation.ADD,
        3,
        "map:test-case",
        "integration"
      ).validated();
      ProgressResult first = database.applyProgress(request, category);
      assertFalse(first.duplicate());
      assertEquals(3, first.progress());
      assertEquals(List.of(1, 2), first.newlyUnlockedTiers());
      assertEquals(1, database.unlockCounts().get(category.id()).get(1));
      assertEquals(1, database.unlockCounts().get(category.id()).get(2));

      ProgressResult duplicate = database.applyProgress(request, category);
      assertTrue(duplicate.duplicate());
      assertEquals(3, duplicate.progress());

      PlayerState state = database.loadPlayer(
        playerUuid,
        "DatabaseTest",
        new AchievementCatalog(List.of(category))
      );
      assertEquals(3, state.progress().get(category.id()));

      database.replaceSelections(playerUuid, List.of(category.id()));
      PlayerState selected = database.loadPlayer(
        playerUuid,
        "DatabaseTest",
        new AchievementCatalog(List.of(category))
      );
      assertEquals(List.of(category.id()), selected.selectedCategories());

      List<RewardDelivery> rewards = database.claimRewards(10);
      assertEquals(4, rewards.size());
      assertEquals(
        2,
        rewards.stream().filter(reward -> reward.type() == RewardType.ACHIEVEMENT_MESSAGE).count()
      );
      assertEquals(
        2,
        rewards.stream().filter(reward -> reward.type() == RewardType.PERMISSION).count()
      );
      for (RewardDelivery reward : rewards) {
        database.markDelivered(reward.id());
      }
      assertTrue(database.claimRewards(10).isEmpty());

      ProgressResult revoked = database.revoke(
        new AchievementPlayer(playerUuid, "DatabaseTest"),
        category,
        2
      );
      assertEquals(1, revoked.currentTier());
      assertEquals(1, revoked.progress());
      assertEquals(1, database.unlockCounts().get(category.id()).get(1));
      assertFalse(database.unlockCounts().get(category.id()).containsKey(2));

      ProgressResult replayed = database.applyProgress(request, category);
      assertTrue(replayed.duplicate());
      assertEquals(1, replayed.progress());

      ProgressRequest regrant = new ProgressRequest(
        playerUuid.toString(),
        "DatabaseTest",
        category.id(),
        ProgressOperation.MAX,
        3,
        "map:test-case-regrant",
        "integration"
      ).validated();
      ProgressResult regranted = database.applyProgress(regrant, category);
      assertEquals(List.of(2), regranted.newlyUnlockedTiers());
      assertEquals(1, database.unlockCounts().get(category.id()).get(1));
      assertEquals(1, database.unlockCounts().get(category.id()).get(2));
      List<RewardDelivery> regrantRewards = database.claimRewards(10);
      assertEquals(2, regrantRewards.size());
      assertTrue(regrantRewards.stream().anyMatch(
        reward -> reward.type() == RewardType.ACHIEVEMENT_MESSAGE
      ));
      assertTrue(regrantRewards.stream().anyMatch(
        reward -> reward.type() == RewardType.PERMISSION
      ));
      for (RewardDelivery reward : regrantRewards) {
        database.markDelivered(reward.id());
      }

      ProgressResult secondRevoke = database.revoke(
        new AchievementPlayer(playerUuid, "DatabaseTest"),
        category,
        2
      );
      assertEquals(1, secondRevoke.progress());

      ProgressResult reasserted = database.applyProgress(regrant, category);
      assertFalse(reasserted.duplicate());
      assertEquals(3, reasserted.progress());
      assertEquals(List.of(2), reasserted.newlyUnlockedTiers());

      ProgressResult duplicateReassertion = database.applyProgress(regrant, category);
      assertTrue(duplicateReassertion.duplicate());
      assertEquals(3, duplicateReassertion.progress());
      assertTrue(duplicateReassertion.newlyUnlockedTiers().isEmpty());

      List<RewardDelivery> reassertedRewards = database.claimRewards(10);
      assertEquals(2, reassertedRewards.size());
      for (RewardDelivery reward : reassertedRewards) {
        database.markDelivered(reward.id());
      }

      AchievementCategory secondCategory = new AchievementCategory(
        "second_category",
        2,
        false,
        true,
        "Second",
        List.of(),
        "BOOK",
        "◆",
        List.of(new AchievementTier(1, "Tier 1", 1, "#55FF55", List.of()))
      );
      ProgressRequest sharedEventId = new ProgressRequest(
        playerUuid.toString(),
        "DatabaseTest",
        secondCategory.id(),
        ProgressOperation.MAX,
        1,
        "map:test-case",
        "integration"
      ).validated();
      ProgressResult secondResult = database.applyProgress(sharedEventId, secondCategory);
      assertFalse(secondResult.duplicate());
      assertEquals(List.of(1), secondResult.newlyUnlockedTiers());
    }
  }

  @Test
  void migratesARevokedLegacyAssertionAndAllowsItToRunAgain() throws Exception {
    String baseUrl = System.getenv("ACHIEVEMENT_TEST_DATABASE_URL");
    String schema = "achievement_migration_" + UUID.randomUUID().toString().replace("-", "");
    String schemaUrl = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    try (Connection connection = connection(baseUrl); Statement statement = connection.createStatement()) {
      statement.execute("CREATE SCHEMA " + schema);
    }
    try {
      SystemConfig config = config(schemaUrl);
      AchievementCategory category = new AchievementCategory(
        "welcome",
        1,
        false,
        false,
        "Welcome to SHTechCraft Minigames.",
        List.of(),
        "EMERALD",
        "✓",
        List.of(new AchievementTier(
          1,
          "Welcome to SHTechCraft Minigames.",
          1,
          "#55FF55",
          List.of()
        ))
      );
      UUID playerUuid = UUID.randomUUID();
      ProgressRequest request = new ProgressRequest(
        playerUuid.toString(),
        "LegacyWelcome",
        category.id(),
        ProgressOperation.MAX,
        1,
        "welcome:first-login",
        "achievement_system"
      ).validated();
      try (AchievementDatabase database = new AchievementDatabase(config)) {
        assertEquals(List.of(1), database.applyProgress(request, category).newlyUnlockedTiers());
      }
      try (Connection connection = connection(schemaUrl); Statement statement = connection.createStatement()) {
        statement.executeUpdate("UPDATE achievement_progress SET progress = 0");
        statement.executeUpdate("DELETE FROM achievement_unlocks");
        statement.executeUpdate("DELETE FROM achievement_reward_delivery");
        statement.execute("ALTER TABLE achievement_progress DROP COLUMN event_generation");
        statement.execute("ALTER TABLE achievement_events DROP COLUMN operation");
        statement.execute("ALTER TABLE achievement_events DROP COLUMN event_generation");
      }
      try (AchievementDatabase database = new AchievementDatabase(config)) {
        ProgressResult reacquired = database.applyProgress(request, category);
        assertFalse(reacquired.duplicate());
        assertEquals(1, reacquired.progress());
        assertEquals(List.of(1), reacquired.newlyUnlockedTiers());
        assertTrue(database.applyProgress(request, category).duplicate());
        assertEquals(1, database.claimRewards(10).size());
      }
    } finally {
      try (Connection connection = connection(baseUrl); Statement statement = connection.createStatement()) {
        statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
    }
  }

  @Test
  void selectsCurrentFirstUnlockersWithStableOrderingAndRevokeFallback() throws Exception {
    String baseUrl = System.getenv("ACHIEVEMENT_TEST_DATABASE_URL");
    String schema = "achievement_first_unlock_" + UUID.randomUUID().toString().replace("-", "");
    String schemaUrl = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    try (Connection connection = connection(baseUrl); Statement statement = connection.createStatement()) {
      statement.execute("CREATE SCHEMA " + schema);
    }
    try {
      SystemConfig config = config(schemaUrl);
      AchievementCategory category = new AchievementCategory(
        "hidden_history",
        1,
        true,
        false,
        "Hidden History",
        List.of("Hidden description"),
        "BLUE_ICE",
        "?",
        List.of(
          new AchievementTier(1, "First", 1, "#5555FF", List.of()),
          new AchievementTier(2, "Second", 2, "#AA00AA", List.of())
        )
      );
      UUID stableFirstUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
      UUID stableSecondUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
      try (AchievementDatabase database = new AchievementDatabase(config)) {
        assertTrue(database.unlockStatistics().isEmpty());
        database.applyProgress(request(
          stableSecondUuid,
          "SecondPlayer",
          category,
          "second"
        ), category);
        database.applyProgress(request(
          stableFirstUuid,
          "FirstPlayer",
          category,
          "first"
        ), category);
      }
      try (Connection connection = connection(schemaUrl); Statement statement = connection.createStatement()) {
        statement.executeUpdate("""
          UPDATE achievement_unlocks
          SET unlocked_at = TIMESTAMPTZ '2026-01-01 00:00:00+00'
          WHERE category_id = 'hidden_history'
          """);
      }
      try (AchievementDatabase database = new AchievementDatabase(config)) {
        Map<Integer, TierUnlockStatistics> statistics = database.unlockStatistics()
          .get(category.id());
        assertEquals(2, statistics.get(1).unlockedPlayers());
        assertEquals(2, statistics.get(2).unlockedPlayers());
        assertEquals("FirstPlayer", statistics.get(1).firstUnlockerName());
        assertEquals("FirstPlayer", statistics.get(2).firstUnlockerName());

        AchievementManager manager = new AchievementManager(
          database,
          new AchievementCatalog(List.of(category)),
          3,
          () -> {
          }
        );
        PlayerSnapshot hiddenSnapshot = manager.snapshot(
          UUID.fromString("00000000-0000-0000-0000-000000000003"),
          "Viewer"
        );
        var hiddenCategory = hiddenSnapshot.categories().getFirst();
        assertFalse(hiddenCategory.discovered());
        assertEquals("隐藏成就", hiddenCategory.displayName());
        assertEquals(List.of("完成后揭晓"), hiddenCategory.description());
        assertEquals("???", hiddenCategory.tiers().getFirst().name());
        assertEquals("FirstPlayer", hiddenCategory.tiers().getFirst().firstUnlockerName());
        assertTrue(hiddenCategory.tiers().getFirst().rewardDescriptions().isEmpty());

        database.loadPlayer(
          stableFirstUuid,
          "RenamedPlayer",
          new AchievementCatalog(List.of(category))
        );
        Map<Integer, TierUnlockStatistics> afterRename = database.unlockStatistics()
          .get(category.id());
        assertEquals("RenamedPlayer", afterRename.get(1).firstUnlockerName());
        assertEquals("RenamedPlayer", afterRename.get(2).firstUnlockerName());

        database.revoke(
          new AchievementPlayer(stableFirstUuid, "RenamedPlayer"),
          category,
          0
        );
        Map<Integer, TierUnlockStatistics> afterRevoke = database.unlockStatistics()
          .get(category.id());
        assertEquals(1, afterRevoke.get(1).unlockedPlayers());
        assertEquals(1, afterRevoke.get(2).unlockedPlayers());
        assertEquals("SecondPlayer", afterRevoke.get(1).firstUnlockerName());
        assertEquals("SecondPlayer", afterRevoke.get(2).firstUnlockerName());

        database.applyProgress(request(
          stableFirstUuid,
          "RenamedPlayer",
          category,
          "first-reacquire"
        ), category);
        Map<Integer, TierUnlockStatistics> afterReacquire = database.unlockStatistics()
          .get(category.id());
        assertEquals("SecondPlayer", afterReacquire.get(1).firstUnlockerName());
        assertEquals("SecondPlayer", afterReacquire.get(2).firstUnlockerName());

        database.revoke(
          new AchievementPlayer(stableSecondUuid, "SecondPlayer"),
          category,
          0
        );
        Map<Integer, TierUnlockStatistics> afterSecondRevoke = database.unlockStatistics()
          .get(category.id());
        assertEquals("RenamedPlayer", afterSecondRevoke.get(1).firstUnlockerName());
        assertEquals("RenamedPlayer", afterSecondRevoke.get(2).firstUnlockerName());

        database.revoke(
          new AchievementPlayer(stableFirstUuid, "RenamedPlayer"),
          category,
          0
        );
        assertFalse(database.unlockStatistics().containsKey(category.id()));
      }
    } finally {
      try (Connection connection = connection(baseUrl); Statement statement = connection.createStatement()) {
        statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
    }
  }

  private static ProgressRequest request(
    UUID playerUuid,
    String playerName,
    AchievementCategory category,
    String eventId
  ) {
    return new ProgressRequest(
      playerUuid.toString(),
      playerName,
      category.id(),
      ProgressOperation.MAX,
      2,
      eventId,
      "integration"
    ).validated();
  }

  private static SystemConfig config(String databaseUrl) {
    return new SystemConfig(
      "127.0.0.1",
      25567,
      "0123456789abcdef0123456789abcdef",
      65_536,
      databaseUrl,
      System.getenv("ACHIEVEMENT_TEST_DATABASE_USER"),
      System.getenv("ACHIEVEMENT_TEST_DATABASE_PASSWORD"),
      2,
      3,
      Duration.ofSeconds(5),
      50
    );
  }

  private static Connection connection(String databaseUrl) throws Exception {
    return DriverManager.getConnection(
      databaseUrl,
      System.getenv("ACHIEVEMENT_TEST_DATABASE_USER"),
      System.getenv("ACHIEVEMENT_TEST_DATABASE_PASSWORD")
    );
  }
}
