package dev.shtech.achievement.system;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;

public final class RewardDispatcher implements AutoCloseable {
  private final AchievementDatabase database;
  private final ProxyServer proxyServer;
  private final LuckPerms luckPerms;
  private final SystemConfig config;
  private final Executor ioExecutor;
  private final Consumer<String> errorLogger;
  private final AtomicBoolean polling = new AtomicBoolean();
  private final AtomicBoolean wakeRequested = new AtomicBoolean();
  private volatile ScheduledTask scheduledTask;

  public RewardDispatcher(
    AchievementDatabase database,
    ProxyServer proxyServer,
    LuckPerms luckPerms,
    SystemConfig config,
    Executor ioExecutor,
    Consumer<String> errorLogger
  ) {
    this.database = database;
    this.proxyServer = proxyServer;
    this.luckPerms = luckPerms;
    this.config = config;
    this.ioExecutor = ioExecutor;
    this.errorLogger = errorLogger;
  }

  public void start(Object plugin) {
    scheduledTask = proxyServer.getScheduler()
      .buildTask(plugin, this::wake)
      .delay(1, TimeUnit.SECONDS)
      .repeat(config.rewardPollInterval())
      .schedule();
  }

  public void wake() {
    wakeRequested.set(true);
    startPolling();
  }

  private void startPolling() {
    if (polling.compareAndSet(false, true)) {
      ioExecutor.execute(this::poll);
    }
  }

  private void poll() {
    wakeRequested.set(false);
    try {
      List<RewardDelivery> deliveries = database.claimRewards(config.rewardBatchSize());
      if (deliveries.isEmpty()) {
        polling.set(false);
        if (wakeRequested.get()) {
          startPolling();
        }
        return;
      }
      CompletableFuture<?>[] tasks = deliveries.stream()
        .map(this::deliverAndRecord)
        .toArray(CompletableFuture[]::new);
      CompletableFuture.allOf(tasks).whenCompleteAsync((ignored, error) -> {
        polling.set(false);
        wake();
      }, ioExecutor);
    } catch (Exception error) {
      polling.set(false);
      errorLogger.accept("Failed to poll achievement rewards: " + rootMessage(error));
      if (wakeRequested.get()) {
        startPolling();
      }
    }
  }

  private CompletableFuture<Void> deliverAndRecord(RewardDelivery delivery) {
    CompletableFuture<Void> delivered;
    try {
      delivered = deliver(delivery);
    } catch (RuntimeException error) {
      delivered = CompletableFuture.failedFuture(error);
    }
    return delivered.orTimeout(30, TimeUnit.SECONDS).handleAsync((ignored, error) -> {
      try {
        if (error == null) {
          database.markDelivered(delivery.id());
        } else {
          database.markRetry(delivery, rootMessage(error));
        }
      } catch (Exception databaseError) {
        errorLogger.accept("Failed to update reward delivery status: " + rootMessage(databaseError));
      }
      return null;
    }, ioExecutor);
  }

  private CompletableFuture<Void> deliver(RewardDelivery delivery) {
    String value = placeholders(delivery.value(), delivery);
    return switch (delivery.type()) {
      case ACHIEVEMENT_MESSAGE -> deliverAchievementMessage(value);
      case MESSAGE -> deliverMessage(delivery, value);
      case VELOCITY_COMMAND -> proxyServer.getCommandManager()
        .executeAsync(proxyServer.getConsoleCommandSource(), stripSlash(value))
        .thenCompose(accepted -> accepted
          ? CompletableFuture.completedFuture(null)
          : CompletableFuture.failedFuture(
            new IllegalStateException("Velocity command was not accepted.")
          ));
      case PERMISSION -> luckPerms.getUserManager().loadUser(delivery.playerUuid())
        .thenCompose(user -> {
          user.data().add(PermissionNode.builder(value).build());
          return luckPerms.getUserManager().saveUser(user);
        });
      case GROUP -> luckPerms.getUserManager().loadUser(delivery.playerUuid())
        .thenCompose(user -> {
          user.data().add(InheritanceNode.builder(value).build());
          return luckPerms.getUserManager().saveUser(user);
        });
    };
  }

  private CompletableFuture<Void> deliverAchievementMessage(String value) {
    proxyServer.sendMessage(MiniMessage.miniMessage().deserialize(value));
    return CompletableFuture.completedFuture(null);
  }

  private CompletableFuture<Void> deliverMessage(RewardDelivery delivery, String value) {
    Optional<Player> player = proxyServer.getPlayer(delivery.playerUuid());
    if (player.isEmpty()) {
      return CompletableFuture.failedFuture(new IllegalStateException("Player is offline."));
    }
    player.get().sendMessage(MiniMessage.miniMessage().deserialize(value));
    return CompletableFuture.completedFuture(null);
  }

  private static String placeholders(String value, RewardDelivery delivery) {
    return value
      .replace("{player}", MiniMessage.miniMessage().escapeTags(delivery.playerName()))
      .replace("{uuid}", delivery.playerUuid().toString())
      .replace("{category}", delivery.categoryId())
      .replace("{tier}", Integer.toString(delivery.tier()));
  }

  private static String stripSlash(String value) {
    return value.startsWith("/") ? value.substring(1) : value;
  }

  private static String rootMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }

  @Override
  public void close() {
    ScheduledTask task = scheduledTask;
    if (task != null) {
      task.cancel();
    }
  }
}
