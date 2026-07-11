package dev.shtech.achievement.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class AchievementCommand implements CommandExecutor {
  private final AchievementGuiController controller;

  AchievementCommand(AchievementGuiController controller) {
    this.controller = controller;
  }

  @Override
  public boolean onCommand(
    @NotNull CommandSender sender,
    @NotNull Command command,
    @NotNull String label,
    @NotNull String[] arguments
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("This command can only be used by a player.");
      return true;
    }
    if (arguments.length != 0) {
      player.sendMessage(Component.text("Usage: /" + label, NamedTextColor.RED));
      return true;
    }
    controller.open(player);
    return true;
  }
}
