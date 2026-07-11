package dev.shtech.achievement.system;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class AchievementRootCommand implements SimpleCommand {
  private final AchievementAdminCommand adminCommand;

  public AchievementRootCommand(AchievementAdminCommand adminCommand) {
    this.adminCommand = adminCommand;
  }

  @Override
  public void execute(Invocation invocation) {
    String[] arguments = invocation.arguments();
    if (arguments.length == 0) {
      openGui(invocation.source());
      return;
    }
    if (!invocation.source().hasPermission("achievement.admin")) {
      invocation.source().sendMessage(Component.text(
        "You do not have permission.",
        NamedTextColor.RED
      ));
      return;
    }
    if (!arguments[0].equalsIgnoreCase("admin")) {
      sendUsage(invocation.source());
      return;
    }
    adminCommand.execute(
      invocation.source(),
      Arrays.copyOfRange(arguments, 1, arguments.length)
    );
  }

  @Override
  public List<String> suggest(Invocation invocation) {
    String[] arguments = invocation.arguments();
    if (arguments.length <= 1) {
      if (!invocation.source().hasPermission("achievement.admin")) {
        return List.of();
      }
      String partial = arguments.length == 0 ? "" : arguments[0].toLowerCase();
      return "admin".startsWith(partial) ? List.of("admin") : List.of();
    }
    if (!arguments[0].equalsIgnoreCase("admin")
      || !invocation.source().hasPermission("achievement.admin")) {
      return List.of();
    }
    return adminCommand.suggest(Arrays.copyOfRange(arguments, 1, arguments.length));
  }

  private static void openGui(CommandSource source) {
    if (!(source instanceof Player player)) {
      source.sendMessage(Component.text(
        "Only players can open the achievement GUI.",
        NamedTextColor.RED
      ));
      return;
    }
    if (player.getCurrentServer().isEmpty()) {
      player.sendMessage(Component.text("You are not connected to a server.", NamedTextColor.RED));
      return;
    }
    player.spoofChatInput("/achievement");
  }

  private static void sendUsage(CommandSource source) {
    source.sendMessage(Component.text(
      "Usage: /achievements [admin <command>]",
      NamedTextColor.YELLOW
    ));
  }
}
