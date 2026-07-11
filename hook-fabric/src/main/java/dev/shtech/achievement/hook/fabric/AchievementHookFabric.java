package dev.shtech.achievement.hook.fabric;

import dev.shtech.achievement.hook.AchievementHook;
import dev.shtech.achievement.hook.AchievementService;
import dev.shtech.achievement.hook.HttpAchievementService;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class AchievementHookFabric implements ModInitializer {
  private static final System.Logger LOGGER = System.getLogger("AchievementHook");
  private static AchievementService service;
  private static Thread shutdownThread;

  @Override
  public void onInitialize() {
    Path configPath = FabricLoader.getInstance()
      .getConfigDir()
      .resolve("achievement-hook.json");
    FabricHookConfig config;
    try {
      config = FabricHookConfig.loadOrCreate(configPath);
    } catch (Exception error) {
      LOGGER.log(
        System.Logger.Level.ERROR,
        "Achievement Hook is disabled because its configuration could not be loaded.",
        error
      );
      return;
    }
    if (!config.enabled()) {
      LOGGER.log(System.Logger.Level.INFO, "Achievement Hook is disabled by configuration.");
      return;
    }
    FabricHookConfig.Settings settings;
    try {
      settings = config.settings(System.getenv());
    } catch (RuntimeException error) {
      LOGGER.log(
        System.Logger.Level.ERROR,
        "Achievement Hook is disabled: " + error.getMessage()
      );
      return;
    }
    AchievementService created;
    try {
      created = new HttpAchievementService(
        settings.apiUrl(),
        settings.apiToken(),
        settings.timeout(),
        settings.source(),
        settings.retryAttempts()
      );
    } catch (RuntimeException error) {
      LOGGER.log(
        System.Logger.Level.ERROR,
        "Achievement Hook is disabled: " + error.getMessage()
      );
      return;
    }
    if (!AchievementHook.install(created)) {
      created.close();
      LOGGER.log(
        System.Logger.Level.ERROR,
        "Achievement Hook is disabled because another service is already installed."
      );
      return;
    }
    synchronized (AchievementHookFabric.class) {
      service = created;
      shutdownThread = new Thread(AchievementHookFabric::shutdown, "achievement-hook-shutdown");
      Runtime.getRuntime().addShutdownHook(shutdownThread);
    }
    LOGGER.log(
      System.Logger.Level.INFO,
      "Achievement Hook enabled with source " + settings.source() + "."
    );
  }

  private static synchronized void shutdown() {
    AchievementService current = service;
    service = null;
    shutdownThread = null;
    if (current != null) {
      AchievementHook.uninstall(current);
      current.close();
    }
  }
}
