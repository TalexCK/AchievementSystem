package dev.shtech.achievement.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

class AchievementCommandPermissionTest {
  @Test
  void rejectsEveryRootArgumentWithoutAdminPermission() {
    List<Component> messages = new ArrayList<>();
    CommandSource source = source(false, messages);
    AchievementRootCommand command = new AchievementRootCommand(
      new AchievementAdminCommand(null)
    );

    command.execute(new Invocation(source, "achievements", new String[]{"unknown"}));

    assertEquals(1, messages.size());
    assertEquals("You do not have permission.", text(messages.getFirst()));
  }

  @Test
  void keepsParameterlessRootCommandPublic() {
    List<Component> messages = new ArrayList<>();
    CommandSource source = source(false, messages);
    AchievementRootCommand command = new AchievementRootCommand(
      new AchievementAdminCommand(null)
    );

    command.execute(new Invocation(source, "achievements", new String[0]));

    assertEquals(1, messages.size());
    assertEquals("Only players can open the achievement GUI.", text(messages.getFirst()));
  }

  @Test
  void hidesAdminCompletionsFromOrdinaryPlayers() {
    CommandSource source = source(false, new ArrayList<>());
    AchievementRootCommand root = new AchievementRootCommand(new AchievementAdminCommand(null));
    AchievementAdminCommand admin = new AchievementAdminCommand(null);

    assertTrue(root.suggest(new Invocation(source, "achievements", new String[0])).isEmpty());
    assertTrue(root.suggest(
      new Invocation(source, "achievements", new String[]{"admin", ""})
    ).isEmpty());
    assertFalse(admin.hasPermission(new Invocation(source, "achievementsystem", new String[0])));
    assertTrue(admin.suggest(new Invocation(
      source,
      "achievementsystem",
      new String[0]
    )).isEmpty());
  }

  @Test
  void exposesAdminCommandsOnlyWithTheExplicitPermission() {
    CommandSource source = source(true, new ArrayList<>());
    AchievementRootCommand root = new AchievementRootCommand(new AchievementAdminCommand(null));
    AchievementAdminCommand admin = new AchievementAdminCommand(null);

    assertEquals(
      List.of("admin"),
      root.suggest(new Invocation(source, "achievements", new String[0]))
    );
    assertTrue(admin.hasPermission(new Invocation(source, "achievementsystem", new String[0])));
  }

  private static CommandSource source(boolean permitted, List<Component> messages) {
    return (CommandSource) Proxy.newProxyInstance(
      AchievementCommandPermissionTest.class.getClassLoader(),
      new Class<?>[]{CommandSource.class},
      (proxy, method, arguments) -> switch (method.getName()) {
        case "hasPermission" -> permitted;
        case "sendMessage" -> {
          if (arguments != null && arguments.length > 0 && arguments[0] instanceof Component value) {
            messages.add(value);
          }
          yield null;
        }
        case "toString" -> "TestCommandSource";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == arguments[0];
        default -> null;
      }
    );
  }

  private static String text(Component component) {
    return component instanceof net.kyori.adventure.text.TextComponent value
      ? value.content()
      : component.toString();
  }

  private record Invocation(
    CommandSource source,
    String alias,
    String[] arguments
  ) implements SimpleCommand.Invocation {
  }
}
