package dev.shtech.achievement.hook.paper;

import dev.shtech.achievement.hook.AchievementHook;
import dev.shtech.achievement.hook.AchievementService;
import dev.shtech.achievement.hook.HttpAchievementService;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class AchievementHookPaperPlugin extends JavaPlugin {
  private AchievementService service;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    PaperHookSettings settings;
    try {
      settings = PaperHookSettings.load(getConfig(), System.getenv());
    } catch (RuntimeException error) {
      disable("Invalid configuration: " + error.getMessage());
      return;
    }
    if (!settings.enabled()) {
      disable("Achievement Hook is disabled by configuration.");
      return;
    }
    try {
      service = new HttpAchievementService(
        settings.apiUrl(),
        settings.apiToken(),
        settings.timeout(),
        settings.source(),
        settings.retryAttempts()
      );
    } catch (RuntimeException error) {
      disable("Achievement Hook is disabled: " + error.getMessage());
      return;
    }
    if (!AchievementHook.install(service)) {
      disable("Achievement Hook is disabled because another service is already installed.");
      return;
    }
    getServer().getServicesManager().register(
      AchievementService.class,
      service,
      this,
      ServicePriority.Normal
    );
    PluginCommand command = Objects.requireNonNull(
      getCommand("achievementhook"),
      "achievementhook command"
    );
    AchievementHookCommand handler = new AchievementHookCommand(this, service);
    command.setExecutor(handler);
    command.setTabCompleter(handler);
    getLogger().info("Achievement Hook enabled with source " + settings.source() + ".");
  }

  private void disable(String message) {
    getLogger().severe(message);
    getServer().getPluginManager().disablePlugin(this);
  }

  @Override
  public void onDisable() {
    getServer().getServicesManager().unregisterAll(this);
    AchievementService current = service;
    service = null;
    if (current != null) {
      AchievementHook.uninstall(current);
      current.close();
    }
  }
}
