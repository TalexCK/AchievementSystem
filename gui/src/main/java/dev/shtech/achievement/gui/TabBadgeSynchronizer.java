package dev.shtech.achievement.gui;

import dev.shtech.achievement.common.AchievementApiClient;
import dev.shtech.achievement.common.BadgeSnapshot;
import dev.shtech.achievement.common.PlayerSnapshot;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.event.player.PlayerLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

final class TabBadgeSynchronizer implements Listener {
  private static final long[] RETRY_DELAYS = {1L, 5L, 20L, 40L};
  private final JavaPlugin plugin;
  private final AchievementApiClient client;
  private final TabAPI tabApi;
  private final AtomicLong sequence = new AtomicLong();
  private final ConcurrentHashMap<UUID, Long> requests = new ConcurrentHashMap<>();
  private final me.neznamy.tab.api.event.EventHandler<PlayerLoadEvent> playerLoadHandler =
    this::onTabPlayerLoad;
  private volatile boolean enabled;

  TabBadgeSynchronizer(JavaPlugin plugin, AchievementApiClient client) {
    this.plugin = plugin;
    this.client = client;
    this.tabApi = TabAPI.getInstance();
    if (tabApi == null
      || tabApi.getTabListFormatManager() == null
      || tabApi.getNameTagManager() == null) {
      throw new IllegalStateException("TAB name formatting features are unavailable.");
    }
  }

  void enable() {
    if (enabled) {
      return;
    }
    enabled = true;
    tabApi.getEventBus().register(PlayerLoadEvent.class, playerLoadHandler);
    Bukkit.getOnlinePlayers().forEach(this::refresh);
  }

  void disable() {
    if (!enabled) {
      return;
    }
    tabApi.getEventBus().unregister(playerLoadHandler);
    Bukkit.getOnlinePlayers().forEach(player -> clear(player.getUniqueId()));
    requests.clear();
    enabled = false;
  }

  void refresh(Player player) {
    refresh(player.getUniqueId(), player.getName());
  }

  void applySnapshot(Player player, PlayerSnapshot snapshot) {
    UUID playerUuid = player.getUniqueId();
    long request = nextRequest(playerUuid);
    List<BadgeSnapshot> badges = List.copyOf(snapshot.selectedBadges());
    runSync(() -> applyWhenLoaded(playerUuid, badges, request, 0));
  }

  @EventHandler
  public void onPlayerQuit(PlayerQuitEvent event) {
    requests.remove(event.getPlayer().getUniqueId());
  }

  private void onTabPlayerLoad(PlayerLoadEvent event) {
    refresh(event.getPlayer().getUniqueId(), event.getPlayer().getName());
  }

  private void refresh(UUID playerUuid, String playerName) {
    if (!enabled) {
      return;
    }
    long request = nextRequest(playerUuid);
    client.player(playerUuid, playerName).whenComplete((snapshot, error) -> {
      if (error != null || snapshot == null) {
        if (isCurrent(playerUuid, request)) {
          logFailure("Failed to load achievement badges for " + playerName, error);
        }
        return;
      }
      List<BadgeSnapshot> badges = List.copyOf(snapshot.selectedBadges());
      runSync(() -> applyWhenLoaded(playerUuid, badges, request, 0));
    });
  }

  private void applyWhenLoaded(
    UUID playerUuid,
    List<BadgeSnapshot> badges,
    long request,
    int attempt
  ) {
    if (!enabled || !isCurrent(playerUuid, request)) {
      return;
    }
    Player player = Bukkit.getPlayer(playerUuid);
    TabPlayer tabPlayer = tabApi.getPlayer(playerUuid);
    if (player == null || !player.isOnline()) {
      requests.remove(playerUuid, request);
      return;
    }
    if (tabPlayer == null || !tabPlayer.isLoaded()) {
      if (attempt < RETRY_DELAYS.length) {
        Bukkit.getScheduler().runTaskLater(
          plugin,
          () -> applyWhenLoaded(playerUuid, badges, request, attempt + 1),
          RETRY_DELAYS[attempt]
        );
      } else {
        plugin.getLogger().warning("TAB player data was not ready for " + player.getName() + ".");
      }
      return;
    }
    String suffix = TabBadgeSuffixFormatter.format(badges);
    tabApi.getTabListFormatManager().setSuffix(tabPlayer, suffix);
    tabApi.getNameTagManager().setSuffix(tabPlayer, suffix);
  }

  private void clear(UUID playerUuid) {
    TabPlayer tabPlayer = tabApi.getPlayer(playerUuid);
    if (tabPlayer == null || !tabPlayer.isLoaded()) {
      return;
    }
    tabApi.getTabListFormatManager().setSuffix(tabPlayer, "");
    tabApi.getNameTagManager().setSuffix(tabPlayer, "");
  }

  private long nextRequest(UUID playerUuid) {
    long request = sequence.incrementAndGet();
    requests.put(playerUuid, request);
    return request;
  }

  private boolean isCurrent(UUID playerUuid, long request) {
    return requests.getOrDefault(playerUuid, -1L) == request;
  }

  private void runSync(Runnable task) {
    if (!enabled || !plugin.isEnabled()) {
      return;
    }
    if (Bukkit.isPrimaryThread()) {
      task.run();
      return;
    }
    Bukkit.getScheduler().runTask(plugin, task);
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
}
