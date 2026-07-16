package dev.shtech.achievement.system;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.shtech.achievement.common.ProgressRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AchievementDatabase implements AutoCloseable {
  private final HikariDataSource dataSource;

  public AchievementDatabase(SystemConfig config) throws SQLException {
    HikariConfig hikari = new HikariConfig();
    hikari.setJdbcUrl(config.databaseUrl());
    hikari.setDriverClassName("org.postgresql.Driver");
    hikari.setUsername(config.databaseUser());
    hikari.setPassword(config.databasePassword());
    hikari.setMaximumPoolSize(config.databasePoolSize());
    hikari.setMinimumIdle(1);
    hikari.setConnectionTimeout(5_000);
    hikari.setValidationTimeout(3_000);
    hikari.setPoolName("AchievementSystem");
    this.dataSource = new HikariDataSource(hikari);
    migrate();
  }

  private void migrate() throws SQLException {
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
      boolean eventGenerationExisted = columnExists(
        connection,
        "achievement_progress",
        "event_generation"
      );
      statement.execute("""
        CREATE TABLE IF NOT EXISTS achievement_players (
          player_uuid UUID PRIMARY KEY,
          player_name VARCHAR(32) NOT NULL,
          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
        """);
      statement.execute("""
        CREATE TABLE IF NOT EXISTS achievement_progress (
          player_uuid UUID NOT NULL REFERENCES achievement_players(player_uuid) ON DELETE CASCADE,
          category_id VARCHAR(64) NOT NULL,
          progress BIGINT NOT NULL CHECK (progress >= 0),
          event_generation INTEGER NOT NULL DEFAULT 0 CHECK (event_generation >= 0),
          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          PRIMARY KEY (player_uuid, category_id)
        )
        """);
      statement.execute("""
        ALTER TABLE achievement_progress
        ADD COLUMN IF NOT EXISTS event_generation INTEGER NOT NULL DEFAULT 0
        """);
      statement.execute("""
        CREATE TABLE IF NOT EXISTS achievement_unlocks (
          player_uuid UUID NOT NULL REFERENCES achievement_players(player_uuid) ON DELETE CASCADE,
          category_id VARCHAR(64) NOT NULL,
          tier INTEGER NOT NULL CHECK (tier > 0),
          unlocked_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          PRIMARY KEY (player_uuid, category_id, tier)
        )
        """);
      statement.execute("""
        CREATE TABLE IF NOT EXISTS achievement_badge_selection (
          player_uuid UUID NOT NULL REFERENCES achievement_players(player_uuid) ON DELETE CASCADE,
          slot SMALLINT NOT NULL CHECK (slot >= 0 AND slot < 3),
          category_id VARCHAR(64) NOT NULL,
          selected_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          PRIMARY KEY (player_uuid, slot),
          UNIQUE (player_uuid, category_id)
        )
        """);
      statement.execute("""
        CREATE TABLE IF NOT EXISTS achievement_events (
          player_uuid UUID NOT NULL REFERENCES achievement_players(player_uuid) ON DELETE CASCADE,
          category_id VARCHAR(64) NOT NULL,
          source VARCHAR(64) NOT NULL,
          event_id VARCHAR(128) NOT NULL,
          operation VARCHAR(8) NOT NULL DEFAULT 'LEGACY',
          event_generation INTEGER NOT NULL DEFAULT 0,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          PRIMARY KEY (player_uuid, category_id, source, event_id)
        )
        """);
      statement.execute("""
        ALTER TABLE achievement_events
        ADD COLUMN IF NOT EXISTS category_id VARCHAR(64)
        """);
      statement.execute("""
        UPDATE achievement_events
        SET category_id = 'legacy'
        WHERE category_id IS NULL
        """);
      statement.execute("""
        ALTER TABLE achievement_events
        ALTER COLUMN category_id SET NOT NULL
        """);
      statement.execute("""
        ALTER TABLE achievement_events
        ADD COLUMN IF NOT EXISTS operation VARCHAR(8) NOT NULL DEFAULT 'LEGACY'
        """);
      statement.execute("""
        ALTER TABLE achievement_events
        ADD COLUMN IF NOT EXISTS event_generation INTEGER NOT NULL DEFAULT 0
        """);
      if (!eventGenerationExisted) {
        statement.executeUpdate("""
          UPDATE achievement_progress progress
          SET event_generation = 1
          WHERE EXISTS (
            SELECT 1
            FROM achievement_events event
            WHERE event.player_uuid = progress.player_uuid
              AND event.category_id = progress.category_id
          )
          """);
      }
      migrateEventPrimaryKey(connection);
      statement.execute("""
        CREATE INDEX IF NOT EXISTS achievement_events_category_idx
        ON achievement_events (player_uuid, category_id)
        """);
      statement.execute("""
        CREATE TABLE IF NOT EXISTS achievement_reward_delivery (
          delivery_id BIGSERIAL PRIMARY KEY,
          player_uuid UUID NOT NULL REFERENCES achievement_players(player_uuid) ON DELETE CASCADE,
          category_id VARCHAR(64) NOT NULL,
          tier INTEGER NOT NULL,
          reward_index INTEGER NOT NULL,
          reward_type VARCHAR(32) NOT NULL,
          reward_value VARCHAR(512) NOT NULL,
          status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
          attempts INTEGER NOT NULL DEFAULT 0,
          next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          last_error VARCHAR(512),
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          UNIQUE (player_uuid, category_id, tier, reward_index)
        )
        """);
      statement.execute("""
        CREATE INDEX IF NOT EXISTS achievement_reward_pending_idx
        ON achievement_reward_delivery (status, next_attempt_at, delivery_id)
        """);
      statement.execute("""
        CREATE INDEX IF NOT EXISTS achievement_unlock_counts_idx
        ON achievement_unlocks (category_id, tier)
        """);
      statement.execute("""
        UPDATE achievement_reward_delivery
        SET status = 'PENDING', next_attempt_at = NOW(), updated_at = NOW()
        WHERE status = 'PROCESSING' AND updated_at < NOW() - INTERVAL '5 minutes'
        """);
    }
  }

  public ProgressResult applyProgress(
    ProgressRequest request,
    AchievementCategory category
  ) throws SQLException {
    UUID playerUuid = UUID.fromString(request.playerUuid());
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        upsertPlayer(connection, playerUuid, request.playerName());
        ensureProgressRow(connection, playerUuid, category.id());
        LockedProgress locked = lockProgress(connection, playerUuid, category.id());
        long current = locked.progress();
        if (request.eventId() != null
          && !insertEvent(connection, playerUuid, request, locked.eventGeneration())) {
          connection.commit();
          return new ProgressResult(true, current, category.currentTier(current), List.of());
        }
        long updated = switch (request.operation()) {
          case ADD -> Math.addExact(current, request.amount());
          case SET -> {
            if (request.amount() < current) {
              throw new IllegalArgumentException("Achievement progress cannot decrease.");
            }
            yield request.amount();
          }
          case MAX -> Math.max(current, request.amount());
        };
        if (updated > 9_000_000_000_000_000L) {
          throw new IllegalArgumentException("Progress value is too large.");
        }
        updateProgress(connection, playerUuid, category.id(), updated);
        List<Integer> unlocked = ensureUnlocks(connection, playerUuid, category, updated);
        connection.commit();
        return new ProgressResult(false, updated, category.currentTier(updated), unlocked);
      } catch (Exception error) {
        connection.rollback();
        if (error instanceof SQLException sqlError) {
          throw sqlError;
        }
        if (error instanceof RuntimeException runtimeError) {
          throw runtimeError;
        }
        throw new SQLException("Failed to update achievement progress.", error);
      }
    }
  }

  public PlayerState loadPlayer(
    UUID playerUuid,
    String playerName,
    AchievementCatalog catalog
  ) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        upsertPlayer(connection, playerUuid, playerName);
        for (AchievementCategory category : catalog.categories()) {
          ensureProgressRow(connection, playerUuid, category.id());
        }
        Map<String, Long> progress = readProgress(connection, playerUuid);
        for (AchievementCategory category : catalog.categories()) {
          long value = progress.getOrDefault(category.id(), 0L);
          ensureUnlocks(connection, playerUuid, category, value);
        }
        List<String> selections = readSelections(connection, playerUuid);
        connection.commit();
        return new PlayerState(playerUuid, playerName, progress, selections);
      } catch (Exception error) {
        connection.rollback();
        if (error instanceof SQLException sqlError) {
          throw sqlError;
        }
        throw new SQLException("Failed to load achievement player.", error);
      }
    }
  }

  public void replaceSelections(UUID playerUuid, List<String> categoryIds) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (PreparedStatement delete = connection.prepareStatement(
        "DELETE FROM achievement_badge_selection WHERE player_uuid = ?"
      )) {
        delete.setObject(1, playerUuid);
        delete.executeUpdate();
        try (PreparedStatement insert = connection.prepareStatement("""
          INSERT INTO achievement_badge_selection (player_uuid, slot, category_id)
          VALUES (?, ?, ?)
          """)) {
          for (int slot = 0; slot < categoryIds.size(); slot++) {
            insert.setObject(1, playerUuid);
            insert.setInt(2, slot);
            insert.setString(3, categoryIds.get(slot));
            insert.addBatch();
          }
          insert.executeBatch();
        }
        connection.commit();
      } catch (SQLException error) {
        connection.rollback();
        throw error;
      }
    }
  }

  public String playerName(UUID playerUuid) throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement statement =
      connection.prepareStatement("""
        SELECT player_name
        FROM achievement_players
        WHERE player_uuid = ?
        """)) {
      statement.setObject(1, playerUuid);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          throw new IllegalArgumentException("Achievement player is not registered.");
        }
        return result.getString(1);
      }
    }
  }

  public AchievementPlayer requirePlayer(String target) throws SQLException {
    if (target == null || target.isBlank()) {
      throw new IllegalArgumentException("Player is required.");
    }
    String normalized = target.trim();
    UUID playerUuid = null;
    try {
      playerUuid = UUID.fromString(normalized);
    } catch (IllegalArgumentException ignored) {
    }
    String sql = playerUuid == null
      ? """
        SELECT player_uuid, player_name
        FROM achievement_players
        WHERE LOWER(player_name) = LOWER(?)
        ORDER BY updated_at DESC
        LIMIT 1
        """
      : """
        SELECT player_uuid, player_name
        FROM achievement_players
        WHERE player_uuid = ?
        """;
    try (Connection connection = dataSource.getConnection();
      PreparedStatement statement = connection.prepareStatement(sql)) {
      if (playerUuid == null) {
        statement.setString(1, normalized);
      } else {
        statement.setObject(1, playerUuid);
      }
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          throw new IllegalArgumentException("Achievement player was not found.");
        }
        return new AchievementPlayer(
          result.getObject(1, UUID.class),
          result.getString(2)
        );
      }
    }
  }

  public ProgressResult revoke(
    AchievementPlayer player,
    AchievementCategory category,
    int tier
  ) throws SQLException {
    int firstRemovedTier = tier <= 0 ? 1 : tier;
    category.tier(firstRemovedTier);
    long maximumProgress = firstRemovedTier == 1
      ? 0
      : category.tier(firstRemovedTier - 1).threshold();
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        ensureProgressRow(connection, player.uuid(), category.id());
        LockedProgress locked = lockProgress(connection, player.uuid(), category.id());
        long current = locked.progress();
        long updated = Math.min(current, maximumProgress);
        revokeProgress(connection, player.uuid(), category.id(), updated);
        deleteTierRange(
          connection,
          "achievement_unlocks",
          player.uuid(),
          category.id(),
          firstRemovedTier
        );
        deleteTierRange(
          connection,
          "achievement_reward_delivery",
          player.uuid(),
          category.id(),
          firstRemovedTier
        );
        if (category.currentTier(updated) == 0) {
          try (PreparedStatement statement = connection.prepareStatement("""
            DELETE FROM achievement_badge_selection
            WHERE player_uuid = ? AND category_id = ?
            """)) {
            statement.setObject(1, player.uuid());
            statement.setString(2, category.id());
            statement.executeUpdate();
          }
        }
        connection.commit();
        return new ProgressResult(false, updated, category.currentTier(updated), List.of());
      } catch (Exception error) {
        connection.rollback();
        if (error instanceof SQLException sqlError) {
          throw sqlError;
        }
        if (error instanceof RuntimeException runtimeError) {
          throw runtimeError;
        }
        throw new SQLException("Failed to revoke achievement progress.", error);
      }
    }
  }

  public List<RewardDelivery> claimRewards(int batchSize) throws SQLException {
    String sql = """
      WITH picked AS (
        SELECT delivery_id
        FROM achievement_reward_delivery
        WHERE status = 'PENDING' AND next_attempt_at <= NOW()
        ORDER BY delivery_id
        FOR UPDATE SKIP LOCKED
        LIMIT ?
      )
      UPDATE achievement_reward_delivery delivery
      SET status = 'PROCESSING', attempts = attempts + 1, updated_at = NOW()
      FROM picked, achievement_players player
      WHERE delivery.delivery_id = picked.delivery_id
        AND player.player_uuid = delivery.player_uuid
      RETURNING delivery.delivery_id, delivery.player_uuid, player.player_name,
        delivery.category_id, delivery.tier, delivery.reward_type,
        delivery.reward_value, delivery.attempts
      """;
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        try (Statement reclaim = connection.createStatement()) {
          reclaim.executeUpdate("""
            UPDATE achievement_reward_delivery
            SET status = 'PENDING', next_attempt_at = NOW(), updated_at = NOW()
            WHERE status = 'PROCESSING'
              AND updated_at < NOW() - INTERVAL '5 minutes'
            """);
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
          statement.setInt(1, batchSize);
          List<RewardDelivery> deliveries = new ArrayList<>();
          try (ResultSet result = statement.executeQuery()) {
            while (result.next()) {
              deliveries.add(new RewardDelivery(
                result.getLong(1),
                result.getObject(2, UUID.class),
                result.getString(3),
                result.getString(4),
                result.getInt(5),
                RewardType.valueOf(result.getString(6)),
                result.getString(7),
                result.getInt(8)
              ));
            }
          }
          connection.commit();
          return deliveries;
        }
      } catch (SQLException error) {
        connection.rollback();
        throw error;
      }
    }
  }

  public void markDelivered(long deliveryId) throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement statement =
      connection.prepareStatement("""
        UPDATE achievement_reward_delivery
        SET status = 'DELIVERED', last_error = NULL, updated_at = NOW()
        WHERE delivery_id = ? AND status = 'PROCESSING'
        """)) {
      statement.setLong(1, deliveryId);
      statement.executeUpdate();
    }
  }

  public void markRetry(RewardDelivery delivery, String error) throws SQLException {
    boolean offlineMessage = delivery.type() == RewardType.MESSAGE
      && "Player is offline.".equals(error);
    boolean failed = delivery.attempts() >= 10 && !offlineMessage;
    long delaySeconds = Math.min(300L, 5L << Math.min(6, Math.max(0, delivery.attempts() - 1)));
    try (Connection connection = dataSource.getConnection(); PreparedStatement statement =
      connection.prepareStatement("""
        UPDATE achievement_reward_delivery
        SET status = ?, next_attempt_at = NOW() + (? * INTERVAL '1 second'),
          last_error = ?, updated_at = NOW()
        WHERE delivery_id = ? AND status = 'PROCESSING'
        """)) {
      statement.setString(1, failed ? "FAILED" : "PENDING");
      statement.setLong(2, delaySeconds);
      statement.setString(3, truncate(error, 512));
      statement.setLong(4, delivery.id());
      statement.executeUpdate();
    }
  }

  public boolean ping() {
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
      try (ResultSet result = statement.executeQuery("SELECT 1")) {
        return result.next() && result.getInt(1) == 1;
      }
    } catch (SQLException error) {
      return false;
    }
  }

  public RewardQueueStatus rewardQueueStatus() throws SQLException {
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
      ResultSet result = statement.executeQuery("""
        SELECT
          COUNT(*) FILTER (WHERE status = 'PENDING'),
          COUNT(*) FILTER (WHERE status = 'PROCESSING'),
          COUNT(*) FILTER (WHERE status = 'FAILED')
        FROM achievement_reward_delivery
        """)) {
      if (!result.next()) {
        return new RewardQueueStatus(0, 0, 0);
      }
      return new RewardQueueStatus(result.getLong(1), result.getLong(2), result.getLong(3));
    }
  }

  public Map<String, Map<Integer, Long>> unlockCounts() throws SQLException {
    Map<String, Map<Integer, Long>> counts = new HashMap<>();
    for (Map.Entry<String, Map<Integer, TierUnlockStatistics>> category
      : unlockStatistics().entrySet()) {
      Map<Integer, Long> categoryCounts = new HashMap<>();
      for (Map.Entry<Integer, TierUnlockStatistics> tier : category.getValue().entrySet()) {
        categoryCounts.put(tier.getKey(), tier.getValue().unlockedPlayers());
      }
      counts.put(category.getKey(), categoryCounts);
    }
    return counts;
  }

  public Map<String, Map<Integer, TierUnlockStatistics>> unlockStatistics() throws SQLException {
    try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
      ResultSet result = statement.executeQuery("""
        SELECT category_id, tier, unlocked_players, player_name
        FROM (
          SELECT unlock.category_id, unlock.tier, player.player_name,
            COUNT(*) OVER (
              PARTITION BY unlock.category_id, unlock.tier
            ) AS unlocked_players,
            ROW_NUMBER() OVER (
              PARTITION BY unlock.category_id, unlock.tier
              ORDER BY unlock.unlocked_at, unlock.player_uuid
            ) AS position
          FROM achievement_unlocks unlock
          JOIN achievement_players player ON player.player_uuid = unlock.player_uuid
        ) ranked
        WHERE position = 1
        """)) {
      Map<String, Map<Integer, TierUnlockStatistics>> statistics = new HashMap<>();
      while (result.next()) {
        statistics.computeIfAbsent(result.getString(1), ignored -> new HashMap<>())
          .put(result.getInt(2), new TierUnlockStatistics(
            result.getLong(3),
            result.getString(4)
          ));
      }
      return statistics;
    }
  }

  public int retryFailedRewards() throws SQLException {
    try (Connection connection = dataSource.getConnection(); PreparedStatement statement =
      connection.prepareStatement("""
        UPDATE achievement_reward_delivery
        SET status = 'PENDING', attempts = 0, next_attempt_at = NOW(),
          last_error = NULL, updated_at = NOW()
        WHERE status = 'FAILED'
        """)) {
      return statement.executeUpdate();
    }
  }

  private static void upsertPlayer(
    Connection connection,
    UUID playerUuid,
    String playerName
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
      INSERT INTO achievement_players (player_uuid, player_name)
      VALUES (?, ?)
      ON CONFLICT (player_uuid) DO UPDATE
      SET player_name = EXCLUDED.player_name, updated_at = NOW()
      """)) {
      statement.setObject(1, playerUuid);
      statement.setString(2, playerName);
      statement.executeUpdate();
    }
  }

  private static void ensureProgressRow(
    Connection connection,
    UUID playerUuid,
    String categoryId
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
      INSERT INTO achievement_progress (player_uuid, category_id, progress)
      VALUES (?, ?, 0)
      ON CONFLICT DO NOTHING
      """)) {
      statement.setObject(1, playerUuid);
      statement.setString(2, categoryId);
      statement.executeUpdate();
    }
  }

  private static LockedProgress lockProgress(
    Connection connection,
    UUID playerUuid,
    String categoryId
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
      SELECT progress, event_generation FROM achievement_progress
      WHERE player_uuid = ? AND category_id = ?
      FOR UPDATE
      """)) {
      statement.setObject(1, playerUuid);
      statement.setString(2, categoryId);
      try (ResultSet result = statement.executeQuery()) {
        if (!result.next()) {
          throw new SQLException("Achievement progress row is missing.");
        }
        return new LockedProgress(result.getLong(1), result.getInt(2));
      }
    }
  }

  private static boolean insertEvent(
    Connection connection,
    UUID playerUuid,
    ProgressRequest request,
    int eventGeneration
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
      INSERT INTO achievement_events (
        player_uuid, category_id, source, event_id, operation, event_generation
      )
      VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT (player_uuid, category_id, source, event_id) DO UPDATE
      SET operation = EXCLUDED.operation,
        event_generation = EXCLUDED.event_generation,
        created_at = NOW()
      WHERE achievement_events.event_generation < EXCLUDED.event_generation
        AND EXCLUDED.operation IN ('SET', 'MAX')
        AND achievement_events.operation IN ('LEGACY', 'SET', 'MAX')
      """)) {
      statement.setObject(1, playerUuid);
      statement.setString(2, request.categoryId());
      statement.setString(3, request.source());
      statement.setString(4, request.eventId());
      statement.setString(5, request.operation().name());
      statement.setInt(6, eventGeneration);
      return statement.executeUpdate() == 1;
    }
  }

  private static void updateProgress(
    Connection connection,
    UUID playerUuid,
    String categoryId,
    long progress
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
      UPDATE achievement_progress
      SET progress = ?, updated_at = NOW()
      WHERE player_uuid = ? AND category_id = ?
      """)) {
      statement.setLong(1, progress);
      statement.setObject(2, playerUuid);
      statement.setString(3, categoryId);
      statement.executeUpdate();
    }
  }

  private static void revokeProgress(
    Connection connection,
    UUID playerUuid,
    String categoryId,
    long progress
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
      UPDATE achievement_progress
      SET progress = ?, event_generation = event_generation + 1, updated_at = NOW()
      WHERE player_uuid = ? AND category_id = ?
      """)) {
      statement.setLong(1, progress);
      statement.setObject(2, playerUuid);
      statement.setString(3, categoryId);
      statement.executeUpdate();
    }
  }

  private static List<Integer> ensureUnlocks(
    Connection connection,
    UUID playerUuid,
    AchievementCategory category,
    long progress
  ) throws SQLException {
    List<Integer> unlocked = new ArrayList<>();
    for (AchievementTier tier : category.tiers()) {
      if (progress < tier.threshold()) {
        break;
      }
      boolean inserted;
      try (PreparedStatement statement = connection.prepareStatement("""
        INSERT INTO achievement_unlocks (player_uuid, category_id, tier)
        VALUES (?, ?, ?)
        ON CONFLICT DO NOTHING
        """)) {
        statement.setObject(1, playerUuid);
        statement.setString(2, category.id());
        statement.setInt(3, tier.level());
        inserted = statement.executeUpdate() == 1;
      }
      if (!inserted) {
        continue;
      }
      unlocked.add(tier.level());
      insertRewards(connection, playerUuid, category, tier);
    }
    return unlocked;
  }

  private static void insertRewards(
    Connection connection,
    UUID playerUuid,
    AchievementCategory category,
    AchievementTier tier
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
      INSERT INTO achievement_reward_delivery (
        player_uuid, category_id, tier, reward_index, reward_type, reward_value
      ) VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT DO NOTHING
      """)) {
      statement.setObject(1, playerUuid);
      statement.setString(2, category.id());
      statement.setInt(3, tier.level());
      statement.setInt(4, -1);
      statement.setString(5, RewardType.ACHIEVEMENT_MESSAGE.name());
      statement.setString(6, achievementMessage(category, tier));
      statement.addBatch();
      for (int index = 0; index < tier.rewards().size(); index++) {
        RewardDefinition reward = tier.rewards().get(index);
        statement.setObject(1, playerUuid);
        statement.setString(2, category.id());
        statement.setInt(3, tier.level());
        statement.setInt(4, index);
        statement.setString(5, reward.type().name());
        statement.setString(6, reward.value());
        statement.addBatch();
      }
      statement.executeBatch();
    }
  }

  private static String achievementMessage(
    AchievementCategory category,
    AchievementTier tier
  ) {
    String title = category.tiers().size() == 1
      ? category.displayName()
      : category.displayName() + " · " + tier.name();
    return "<gray>{player} 完成了成就 </gray><color:" + tier.color() + ">["
      + escapeMiniMessage(title) + "]</color>";
  }

  private static String escapeMiniMessage(String value) {
    return value.replace("\\", "\\\\").replace("<", "\\<");
  }

  private static Map<String, Long> readProgress(
    Connection connection,
    UUID playerUuid
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
      SELECT category_id, progress
      FROM achievement_progress
      WHERE player_uuid = ?
      FOR UPDATE
      """)) {
      statement.setObject(1, playerUuid);
      Map<String, Long> progress = new HashMap<>();
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          progress.put(result.getString(1), result.getLong(2));
        }
      }
      return progress;
    }
  }

  private static List<String> readSelections(
    Connection connection,
    UUID playerUuid
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
      SELECT category_id
      FROM achievement_badge_selection
      WHERE player_uuid = ?
      ORDER BY slot
      """)) {
      statement.setObject(1, playerUuid);
      List<String> selections = new ArrayList<>();
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          selections.add(result.getString(1));
        }
      }
      return selections;
    }
  }

  private static void deleteTierRange(
    Connection connection,
    String table,
    UUID playerUuid,
    String categoryId,
    int firstTier
  ) throws SQLException {
    if (!table.equals("achievement_unlocks")
      && !table.equals("achievement_reward_delivery")) {
      throw new IllegalArgumentException("Invalid achievement table.");
    }
    try (PreparedStatement statement = connection.prepareStatement(
      "DELETE FROM " + table
        + " WHERE player_uuid = ? AND category_id = ? AND tier >= ?"
    )) {
      statement.setObject(1, playerUuid);
      statement.setString(2, categoryId);
      statement.setInt(3, firstTier);
      statement.executeUpdate();
    }
  }

  private static void migrateEventPrimaryKey(Connection connection) throws SQLException {
    String constraintName = null;
    String columns = null;
    try (PreparedStatement statement = connection.prepareStatement("""
      SELECT constraint_name, STRING_AGG(column_name, ',' ORDER BY ordinal_position)
      FROM information_schema.key_column_usage
      WHERE table_schema = CURRENT_SCHEMA()
        AND table_name = 'achievement_events'
        AND constraint_name IN (
          SELECT constraint_name
          FROM information_schema.table_constraints
          WHERE table_schema = CURRENT_SCHEMA()
            AND table_name = 'achievement_events'
            AND constraint_type = 'PRIMARY KEY'
        )
      GROUP BY constraint_name
      """)) {
      try (ResultSet result = statement.executeQuery()) {
        if (result.next()) {
          constraintName = result.getString(1);
          columns = result.getString(2);
        }
      }
    }
    if ("player_uuid,category_id,source,event_id".equals(columns)) {
      return;
    }
    try (Statement statement = connection.createStatement()) {
      if (constraintName != null) {
        statement.execute(
          "ALTER TABLE achievement_events DROP CONSTRAINT \""
            + constraintName.replace("\"", "\"\"") + "\""
        );
      }
      statement.execute("""
        ALTER TABLE achievement_events
        ADD CONSTRAINT achievement_events_pkey
        PRIMARY KEY (player_uuid, category_id, source, event_id)
        """);
    }
  }

  private static boolean columnExists(
    Connection connection,
    String tableName,
    String columnName
  ) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("""
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = CURRENT_SCHEMA()
        AND table_name = ?
        AND column_name = ?
      """)) {
      statement.setString(1, tableName);
      statement.setString(2, columnName);
      try (ResultSet result = statement.executeQuery()) {
        return result.next();
      }
    }
  }

  private static String truncate(String value, int maximumLength) {
    String safe = value == null || value.isBlank() ? "Unknown reward delivery failure." : value;
    return safe.length() <= maximumLength ? safe : safe.substring(0, maximumLength);
  }

  private record LockedProgress(long progress, int eventGeneration) {
  }

  @Override
  public void close() {
    dataSource.close();
  }
}
