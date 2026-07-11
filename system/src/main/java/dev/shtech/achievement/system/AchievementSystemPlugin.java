package dev.shtech.achievement.system;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.shtech.achievement.common.ProgressRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.slf4j.Logger;

@Plugin(
  id = "achievement-system",
  name = "AchievementSystem",
  version = "0.1.0-SNAPSHOT",
  authors = {"TalexCK"},
  description = "Standalone PostgreSQL achievement authority for Velocity.",
  dependencies = {@Dependency(id = "luckperms")}
)
public final class AchievementSystemPlugin {
  private final ProxyServer proxyServer;
  private final Logger logger;
  private final Path dataDirectory;
  private ExecutorService ioExecutor;
  private AchievementDatabase database;
  private AchievementManager manager;
  private RewardDispatcher rewardDispatcher;
  private AchievementHttpServer httpServer;
  private SystemConfig config;
  private CommandMeta adminCommandMeta;
  private CommandMeta rootCommandMeta;

  @Inject
  public AchievementSystemPlugin(
    ProxyServer proxyServer,
    Logger logger,
    @DataDirectory Path dataDirectory
  ) {
    this.proxyServer = proxyServer;
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  @Subscribe
  public void onInitialize(ProxyInitializeEvent event) {
    ThreadFactory factory = task -> {
      Thread thread = new Thread(task, "achievement-system-io");
      thread.setDaemon(true);
      return thread;
    };
    ioExecutor = Executors.newFixedThreadPool(4, factory);
    try {
      config = SystemConfig.load(dataDirectory);
      AchievementCatalog catalog = AchievementCatalog.load(dataDirectory);
      database = new AchievementDatabase(config);
      LuckPerms luckPerms = LuckPermsProvider.get();
      LuckPermsBadgeService badgeService = new LuckPermsBadgeService(
        luckPerms,
        config.suffixPriority()
      );
      rewardDispatcher = new RewardDispatcher(
        database,
        proxyServer,
        luckPerms,
        config,
        ioExecutor,
        logger::error
      );
      manager = new AchievementManager(
        database,
        catalog,
        badgeService,
        config.maximumSelectedBadges(),
        logger::error,
        rewardDispatcher::wake
      );
      httpServer = new AchievementHttpServer(
        config,
        manager,
        database,
        ioExecutor,
        logger::error
      );
      httpServer.start();
      rewardDispatcher.start(this);
      AchievementAdminCommand adminCommand = new AchievementAdminCommand(this);
      adminCommandMeta = proxyServer.getCommandManager()
        .metaBuilder("achievementsystem")
        .aliases("achievementadmin")
        .plugin(this)
        .build();
      rootCommandMeta = proxyServer.getCommandManager()
        .metaBuilder("achievements")
        .plugin(this)
        .build();
      proxyServer.getCommandManager().register(adminCommandMeta, adminCommand);
      proxyServer.getCommandManager().register(
        rootCommandMeta,
        new AchievementRootCommand(adminCommand)
      );
      logger.info(
        "AchievementSystem enabled with {} categories on {}:{}.",
        catalog.categories().size(),
        config.apiHost(),
        config.apiPort()
      );
    } catch (Exception error) {
      logger.error("AchievementSystem failed to initialize: {}", rootMessage(error));
      closeResources();
    }
  }

  @Subscribe
  public void onPostLogin(PostLoginEvent event) {
    AchievementManager currentManager = manager;
    ExecutorService executor = ioExecutor;
    if (currentManager != null && executor != null && !executor.isShutdown()) {
      executor.execute(() -> currentManager.refreshSuffix(
        event.getPlayer().getUniqueId(),
        event.getPlayer().getUsername()
      ));
    }
  }

  @Subscribe
  public void onServerConnected(ServerConnectedEvent event) {
    if (!WelcomeAchievement.shouldGrant(event.getPreviousServer())) {
      return;
    }
    AchievementManager currentManager = manager;
    ExecutorService executor = ioExecutor;
    if (currentManager != null && executor != null && !executor.isShutdown()) {
      executor.execute(() -> {
        try {
          if (currentManager.catalog().find("welcome").isEmpty()) {
            return;
          }
          currentManager.progress(WelcomeAchievement.request(
            event.getPlayer().getUniqueId(),
            event.getPlayer().getUsername()
          ));
        } catch (Exception error) {
          logger.error(
            "Failed to grant the welcome achievement to {}: {}",
            event.getPlayer().getUsername(),
            rootMessage(error)
          );
        }
      });
    }
  }

  @Subscribe
  public void onShutdown(ProxyShutdownEvent event) {
    closeResources();
  }

  public void submitAdminProgress(
    com.velocitypowered.api.command.CommandSource source,
    ProgressRequest request
  ) {
    AchievementManager currentManager = manager;
    if (currentManager == null || ioExecutor == null) {
      source.sendMessage(Component.text("AchievementSystem is unavailable.", NamedTextColor.RED));
      return;
    }
    ioExecutor.execute(() -> {
      try {
        var response = currentManager.progress(request);
        source.sendMessage(Component.text(
          "Progress updated to " + response.progress() + " at tier " + response.currentTier() + ".",
          NamedTextColor.GREEN
        ));
      } catch (Exception error) {
        source.sendMessage(Component.text(rootMessage(error), NamedTextColor.RED));
      }
    });
  }

  public void submitAdminGrant(
    com.velocitypowered.api.command.CommandSource source,
    String target,
    String categoryId,
    int tier
  ) {
    submitAdminMutation(source, () -> manager.grant(target, categoryId, tier), "granted");
  }

  public void submitAdminRevoke(
    com.velocitypowered.api.command.CommandSource source,
    String target,
    String categoryId,
    int tier
  ) {
    submitAdminMutation(source, () -> manager.revoke(target, categoryId, tier), "revoked");
  }

  public void submitAdminView(
    com.velocitypowered.api.command.CommandSource source,
    String target,
    String categoryId
  ) {
    AchievementManager currentManager = manager;
    if (currentManager == null || ioExecutor == null) {
      source.sendMessage(Component.text("AchievementSystem is unavailable.", NamedTextColor.RED));
      return;
    }
    ioExecutor.execute(() -> {
      try {
        List<AdminAchievementResult> results = currentManager.view(target, categoryId);
        if (results.isEmpty()) {
          source.sendMessage(Component.text("No achievement progress was found.", NamedTextColor.GRAY));
          return;
        }
        AdminAchievementResult first = results.getFirst();
        source.sendMessage(Component.text(
          first.playerName() + " (" + first.playerUuid() + ")",
          NamedTextColor.AQUA
        ));
        for (AdminAchievementResult result : results) {
          source.sendMessage(Component.text(
            result.categoryId() + ": progress=" + result.progress()
              + ", tier=" + result.currentTier(),
            result.currentTier() > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY
          ));
        }
      } catch (Exception error) {
        source.sendMessage(Component.text(rootMessage(error), NamedTextColor.RED));
      }
    });
  }

  public void sendStatus(com.velocitypowered.api.command.CommandSource source) {
    AchievementManager currentManager = manager;
    if (currentManager == null || database == null || config == null) {
      source.sendMessage(Component.text("AchievementSystem is unavailable.", NamedTextColor.RED));
      return;
    }
    ioExecutor.execute(() -> {
      try {
        RewardQueueStatus queue = database.rewardQueueStatus();
        source.sendMessage(Component.text(
          "AchievementSystem: database=" + (database.ping() ? "online" : "offline")
            + ", categories=" + currentManager.catalog().categories().size()
            + ", rewards=" + queue.pending() + "/" + queue.processing() + "/" + queue.failed()
            + ", api=127.0.0.1:" + config.apiPort(),
          NamedTextColor.AQUA
        ));
      } catch (Exception error) {
        source.sendMessage(Component.text(rootMessage(error), NamedTextColor.RED));
      }
    });
  }

  public void retryRewards(com.velocitypowered.api.command.CommandSource source) {
    if (database == null || rewardDispatcher == null || ioExecutor == null) {
      source.sendMessage(Component.text("AchievementSystem is unavailable.", NamedTextColor.RED));
      return;
    }
    ioExecutor.execute(() -> {
      try {
        int retried = database.retryFailedRewards();
        rewardDispatcher.wake();
        source.sendMessage(Component.text(
          "Queued " + retried + " failed reward deliveries for retry.",
          NamedTextColor.GREEN
        ));
      } catch (Exception error) {
        source.sendMessage(Component.text(rootMessage(error), NamedTextColor.RED));
      }
    });
  }

  public void reload(com.velocitypowered.api.command.CommandSource source) {
    AchievementManager currentManager = manager;
    if (currentManager == null || ioExecutor == null) {
      source.sendMessage(Component.text("AchievementSystem is unavailable.", NamedTextColor.RED));
      return;
    }
    ioExecutor.execute(() -> {
      try {
        AchievementCatalog catalog = AchievementCatalog.load(dataDirectory);
        currentManager.reload(catalog);
        for (var player : proxyServer.getAllPlayers()) {
          currentManager.refreshSuffix(player.getUniqueId(), player.getUsername());
        }
        source.sendMessage(Component.text(
          "Reloaded " + catalog.categories().size() + " achievement categories.",
          NamedTextColor.GREEN
        ));
      } catch (Exception error) {
        source.sendMessage(Component.text(rootMessage(error), NamedTextColor.RED));
      }
    });
  }

  public ProxyServer proxyServer() {
    return proxyServer;
  }

  public AchievementManager manager() {
    return manager;
  }

  private void submitAdminMutation(
    com.velocitypowered.api.command.CommandSource source,
    AdminMutation mutation,
    String verb
  ) {
    if (manager == null || ioExecutor == null) {
      source.sendMessage(Component.text("AchievementSystem is unavailable.", NamedTextColor.RED));
      return;
    }
    ioExecutor.execute(() -> {
      try {
        AdminAchievementResult result = mutation.run();
        source.sendMessage(Component.text(
          "Achievement " + verb + " for " + result.playerName()
            + ": " + result.categoryId()
            + ", progress=" + result.progress()
            + ", tier=" + result.currentTier() + ".",
          NamedTextColor.GREEN
        ));
      } catch (Exception error) {
        source.sendMessage(Component.text(rootMessage(error), NamedTextColor.RED));
      }
    });
  }

  private void closeResources() {
    if (rootCommandMeta != null) {
      proxyServer.getCommandManager().unregister(rootCommandMeta);
      rootCommandMeta = null;
    }
    if (adminCommandMeta != null) {
      proxyServer.getCommandManager().unregister(adminCommandMeta);
      adminCommandMeta = null;
    }
    if (httpServer != null) {
      httpServer.close();
      httpServer = null;
    }
    if (rewardDispatcher != null) {
      rewardDispatcher.close();
      rewardDispatcher = null;
    }
    if (database != null) {
      database.close();
      database = null;
    }
    manager = null;
    if (ioExecutor != null) {
      ioExecutor.shutdownNow();
      ioExecutor = null;
    }
  }

  private static String rootMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }

  @FunctionalInterface
  private interface AdminMutation {
    AdminAchievementResult run() throws Exception;
  }
}
