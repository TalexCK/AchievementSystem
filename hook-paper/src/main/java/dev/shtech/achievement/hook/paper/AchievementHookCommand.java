package dev.shtech.achievement.hook.paper;

import dev.shtech.achievement.common.ProgressResponse;
import dev.shtech.achievement.hook.AchievementService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class AchievementHookCommand implements CommandExecutor, TabCompleter {
  private static final String USAGE =
    "/achievementhook <add|set|max> <player> <category> <amount> [eventId]";
  private final AchievementHookPaperPlugin plugin;
  private final AchievementService service;

  AchievementHookCommand(AchievementHookPaperPlugin plugin, AchievementService service) {
    this.plugin = plugin;
    this.service = service;
  }

  @Override
  public boolean onCommand(
    CommandSender sender,
    Command command,
    String label,
    String[] arguments
  ) {
    if (!sender.hasPermission("achievementhook.admin")) {
      sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
      return true;
    }
    HookCommandRequest request;
    try {
      request = HookCommandRequest.parse(arguments);
    } catch (IllegalArgumentException error) {
      sender.sendMessage(ChatColor.RED + error.getMessage());
      sender.sendMessage(ChatColor.GRAY + USAGE);
      return true;
    }
    PlayerTarget target = resolvePlayer(request.player());
    if (target == null) {
      sender.sendMessage(ChatColor.RED + "Player is not online and has no cached profile.");
      return true;
    }
    sender.sendMessage(ChatColor.GRAY + "Submitting achievement progress...");
    request.submit(service, target.uuid(), target.name())
      .whenComplete((response, error) -> complete(sender, response, error));
    return true;
  }

  private void complete(CommandSender sender, ProgressResponse response, Throwable error) {
    if (!plugin.isEnabled()) {
      return;
    }
    Bukkit.getScheduler().runTask(plugin, () -> {
      if (error != null) {
        Throwable cause = unwrap(error);
        String message = cause.getMessage();
        sender.sendMessage(
          ChatColor.RED + "Achievement request failed: "
            + (message == null || message.isBlank() ? cause.getClass().getSimpleName() : message)
        );
        return;
      }
      if (!response.accepted()) {
        String message = response.error();
        sender.sendMessage(
          ChatColor.RED + (message == null || message.isBlank()
            ? "Achievement request was rejected."
            : message)
        );
        return;
      }
      String duplicate = response.duplicate() ? " (duplicate event)" : "";
      sender.sendMessage(
        ChatColor.GREEN + "Achievement progress updated to " + response.progress()
          + ", tier " + response.currentTier() + duplicate + "."
      );
      if (!response.newlyUnlockedTiers().isEmpty()) {
        sender.sendMessage(
          ChatColor.GOLD + "Unlocked tiers: " + response.newlyUnlockedTiers() + "."
        );
      }
    });
  }

  private static Throwable unwrap(Throwable error) {
    Throwable current = error;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static PlayerTarget resolvePlayer(String input) {
    Player online = Bukkit.getPlayerExact(input);
    if (online != null) {
      return new PlayerTarget(online.getUniqueId(), online.getName());
    }
    UUID uuid = parseUuid(input);
    for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
      String name = player.getName();
      if ((uuid != null && uuid.equals(player.getUniqueId()))
        || (name != null && name.equalsIgnoreCase(input))) {
        return name == null ? null : new PlayerTarget(player.getUniqueId(), name);
      }
    }
    return null;
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException error) {
      return null;
    }
  }

  @Override
  public List<String> onTabComplete(
    CommandSender sender,
    Command command,
    String alias,
    String[] arguments
  ) {
    if (!sender.hasPermission("achievementhook.admin")) {
      return List.of();
    }
    if (arguments.length == 1) {
      return matches(List.of("add", "set", "max"), arguments[0]);
    }
    if (arguments.length == 2) {
      List<String> names = Bukkit.getOnlinePlayers().stream()
        .map(Player::getName)
        .sorted(String.CASE_INSENSITIVE_ORDER)
        .toList();
      return matches(names, arguments[1]);
    }
    return List.of();
  }

  private static List<String> matches(List<String> values, String prefix) {
    String normalized = prefix.toLowerCase(Locale.ROOT);
    List<String> matches = new ArrayList<>();
    for (String value : values) {
      if (value.toLowerCase(Locale.ROOT).startsWith(normalized)) {
        matches.add(value);
      }
    }
    return matches;
  }

  private record PlayerTarget(UUID uuid, String name) {
  }
}
