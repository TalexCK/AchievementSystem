package dev.shtech.achievement.system;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.shtech.achievement.common.ProgressOperation;
import dev.shtech.achievement.common.ProgressRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class AchievementAdminCommand implements SimpleCommand {
  private final AchievementSystemPlugin plugin;

  public AchievementAdminCommand(AchievementSystemPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public void execute(Invocation invocation) {
    execute(invocation.source(), invocation.arguments());
  }

  void execute(CommandSource source, String[] arguments) {
    if (!source.hasPermission("achievement.admin")) {
      source.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
      return;
    }
    if (arguments.length == 0 || arguments[0].equalsIgnoreCase("status")) {
      plugin.sendStatus(source);
      return;
    }
    String action = arguments[0].toLowerCase(Locale.ROOT);
    switch (action) {
      case "reload" -> plugin.reload(source);
      case "retry-rewards" -> plugin.retryRewards(source);
      case "grant", "give" -> grant(source, arguments);
      case "revoke", "remove" -> revoke(source, arguments);
      case "view" -> view(source, arguments);
      case "add", "set", "max" -> progress(source, arguments);
      default -> sendUsage(source);
    }
  }

  private void grant(CommandSource source, String[] arguments) {
    if (arguments.length < 3 || arguments.length > 4) {
      sendUsage(source);
      return;
    }
    int tier;
    try {
      tier = arguments.length == 4 && !arguments[3].equalsIgnoreCase("max")
        ? positiveTier(arguments[3])
        : 0;
    } catch (IllegalArgumentException error) {
      source.sendMessage(Component.text(error.getMessage(), NamedTextColor.RED));
      return;
    }
    plugin.submitAdminGrant(source, arguments[1], arguments[2], tier);
  }

  private void revoke(CommandSource source, String[] arguments) {
    if (arguments.length < 3 || arguments.length > 4) {
      sendUsage(source);
      return;
    }
    int tier;
    try {
      tier = arguments.length == 4 && !arguments[3].equalsIgnoreCase("all")
        ? positiveTier(arguments[3])
        : 0;
    } catch (IllegalArgumentException error) {
      source.sendMessage(Component.text(error.getMessage(), NamedTextColor.RED));
      return;
    }
    plugin.submitAdminRevoke(source, arguments[1], arguments[2], tier);
  }

  private void view(CommandSource source, String[] arguments) {
    if (arguments.length < 2 || arguments.length > 3) {
      sendUsage(source);
      return;
    }
    plugin.submitAdminView(
      source,
      arguments[1],
      arguments.length == 3 ? arguments[2] : null
    );
  }

  private void progress(CommandSource source, String[] arguments) {
    if (arguments.length < 4 || arguments.length > 5) {
      sendUsage(source);
      return;
    }
    ProgressOperation operation = switch (arguments[0].toLowerCase(Locale.ROOT)) {
      case "add" -> ProgressOperation.ADD;
      case "set" -> ProgressOperation.SET;
      case "max" -> ProgressOperation.MAX;
      default -> throw new IllegalStateException("Invalid progress operation.");
    };
    Player player = resolveOnlinePlayer(arguments[1]);
    if (player == null) {
      source.sendMessage(Component.text("Player is not online.", NamedTextColor.RED));
      return;
    }
    long amount;
    try {
      amount = Long.parseLong(arguments[3]);
    } catch (NumberFormatException error) {
      source.sendMessage(Component.text("Amount must be an integer.", NamedTextColor.RED));
      return;
    }
    String eventId = arguments.length == 5 ? arguments[4] : UUID.randomUUID().toString();
    plugin.submitAdminProgress(source, new ProgressRequest(
      player.getUniqueId().toString(),
      player.getUsername(),
      arguments[2],
      operation,
      amount,
      eventId,
      "achievement_admin"
    ));
  }

  private Player resolveOnlinePlayer(String value) {
    return plugin.proxyServer().getPlayer(value).orElseGet(() -> {
      try {
        return plugin.proxyServer().getPlayer(UUID.fromString(value)).orElse(null);
      } catch (IllegalArgumentException error) {
        return null;
      }
    });
  }

  @Override
  public List<String> suggest(Invocation invocation) {
    if (!invocation.source().hasPermission("achievement.admin")) {
      return List.of();
    }
    return suggest(invocation.arguments());
  }

  @Override
  public boolean hasPermission(Invocation invocation) {
    return invocation.source().hasPermission("achievement.admin");
  }

  List<String> suggest(String[] arguments) {
    if (arguments.length <= 1) {
      return filter(
        List.of(
          "status",
          "reload",
          "retry-rewards",
          "view",
          "grant",
          "revoke",
          "add",
          "set",
          "max"
        ),
        partial(arguments)
      );
    }
    if (arguments.length == 2 && needsPlayer(arguments[0])) {
      return filter(
        plugin.proxyServer().getAllPlayers().stream().map(Player::getUsername).toList(),
        arguments[1]
      );
    }
    if (arguments.length == 3 && needsCategory(arguments[0])) {
      AchievementManager manager = plugin.manager();
      if (manager == null) {
        return List.of();
      }
      return filter(
        manager.catalog().categories().stream().map(AchievementCategory::id).toList(),
        arguments[2]
      );
    }
    if (arguments.length == 4 && isGrant(arguments[0])) {
      return filter(tiers(arguments[2], "max"), arguments[3]);
    }
    if (arguments.length == 4 && isRevoke(arguments[0])) {
      return filter(tiers(arguments[2], "all"), arguments[3]);
    }
    return List.of();
  }

  private List<String> tiers(String categoryId, String special) {
    AchievementManager manager = plugin.manager();
    if (manager == null) {
      return List.of();
    }
    AchievementCategory category = manager.catalog().find(categoryId).orElse(null);
    if (category == null) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    values.add(special);
    for (int tier = 1; tier <= category.tiers().size(); tier++) {
      values.add(Integer.toString(tier));
    }
    return values;
  }

  private static int positiveTier(String value) {
    try {
      int tier = Integer.parseInt(value);
      if (tier < 1 || tier > 10) {
        throw new IllegalArgumentException("Tier must be between 1 and 10.");
      }
      return tier;
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException("Tier must be an integer.");
    }
  }

  private static boolean needsPlayer(String value) {
    return needsCategory(value);
  }

  private static boolean needsCategory(String value) {
    return value.equalsIgnoreCase("view")
      || isGrant(value)
      || isRevoke(value)
      || value.equalsIgnoreCase("add")
      || value.equalsIgnoreCase("set")
      || value.equalsIgnoreCase("max");
  }

  private static boolean isGrant(String value) {
    return value.equalsIgnoreCase("grant") || value.equalsIgnoreCase("give");
  }

  private static boolean isRevoke(String value) {
    return value.equalsIgnoreCase("revoke") || value.equalsIgnoreCase("remove");
  }

  private static String partial(String[] arguments) {
    return arguments.length == 0 ? "" : arguments[arguments.length - 1];
  }

  private static List<String> filter(List<String> values, String partial) {
    String normalized = partial.toLowerCase(Locale.ROOT);
    return values.stream()
      .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
      .toList();
  }

  private static void sendUsage(CommandSource source) {
    for (String line : Arrays.asList(
      "/achievements admin status",
      "/achievements admin reload",
      "/achievements admin retry-rewards",
      "/achievements admin view <player|uuid> [category]",
      "/achievements admin grant <player|uuid> <category> [tier|max]",
      "/achievements admin revoke <player|uuid> <category> [tier|all]"
    )) {
      source.sendMessage(Component.text(line, NamedTextColor.YELLOW));
    }
  }
}
