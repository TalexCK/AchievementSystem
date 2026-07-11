package dev.shtech.achievement.gui;

import dev.shtech.achievement.common.AchievementApiClient;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AchievementGuiPlugin extends JavaPlugin {
  private AchievementGuiController controller;
  private AchievementHotbarEntry hotbarEntry;

  @Override
  public void onEnable() {
    saveDefaultConfig();
    try {
      GuiSettings settings = GuiSettings.load(getConfig(), System::getenv);
      AchievementApiClient client = new AchievementApiClient(
        settings.apiUrl(),
        settings.token(),
        settings.timeout()
      );
      controller = new AchievementGuiController(this, client);
      getServer().getPluginManager().registerEvents(controller, this);
      hotbarEntry = new AchievementHotbarEntry(this, controller::open);
      getServer().getPluginManager().registerEvents(hotbarEntry, this);
      PluginCommand command = Objects.requireNonNull(
        getCommand("achievements"),
        "Achievement command is not registered."
      );
      command.setExecutor(new AchievementCommand(controller));
      hotbarEntry.enable();
      getLogger().info("Achievement GUI enabled.");
    } catch (RuntimeException error) {
      getLogger().severe("Achievement GUI configuration is invalid: " + error.getMessage());
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public void onDisable() {
    if (hotbarEntry != null) {
      hotbarEntry.disable();
    }
    if (controller != null) {
      controller.disable();
    }
  }
}
