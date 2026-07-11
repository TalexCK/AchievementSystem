package dev.shtech.achievement.gui;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import org.bukkit.configuration.ConfigurationSection;

record GuiSettings(String apiUrl, String token, Duration timeout) {
  private static final String TOKEN_PLACEHOLDER = "replace-with-a-long-random-token";

  static GuiSettings load(
    ConfigurationSection configuration,
    Function<String, String> environment
  ) {
    Objects.requireNonNull(configuration, "configuration");
    Objects.requireNonNull(environment, "environment");
    String apiUrl = resolveEnvironment(
      configuration.getString("api-url", "http://127.0.0.1:25567"),
      configuration.getString("api-url-environment", "ACHIEVEMENT_API_URL"),
      environment
    );
    String token = resolveEnvironment(
      configuration.getString("token", TOKEN_PLACEHOLDER),
      configuration.getString("token-environment", "ACHIEVEMENT_API_TOKEN"),
      environment
    );
    long timeoutMillis = configuration.getLong("timeout", 3000L);
    if (timeoutMillis < 100L || timeoutMillis > 60_000L) {
      throw new IllegalArgumentException("Achievement API timeout must be between 100 and 60000 milliseconds.");
    }
    return new GuiSettings(apiUrl, token, Duration.ofMillis(timeoutMillis));
  }

  private static String resolveEnvironment(
    String configured,
    String environmentName,
    Function<String, String> environment
  ) {
    String value = configured == null ? "" : configured.trim();
    String name = environmentName == null ? "" : environmentName.trim();
    if (!name.isEmpty()) {
      String environmentValue = environment.apply(name);
      if (environmentValue != null && !environmentValue.isBlank()) {
        return environmentValue.trim();
      }
    }
    return value;
  }
}
