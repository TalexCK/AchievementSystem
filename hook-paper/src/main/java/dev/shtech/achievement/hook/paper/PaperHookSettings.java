package dev.shtech.achievement.hook.paper;

import java.time.Duration;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;

record PaperHookSettings(
  boolean enabled,
  String apiUrl,
  String apiToken,
  String source,
  Duration timeout,
  int retryAttempts
) {
  static PaperHookSettings load(FileConfiguration config, Map<String, String> environment) {
    String apiUrl = environmentValue(environment, "ACHIEVEMENT_API_URL");
    if (apiUrl == null) {
      apiUrl = config.getString("api-url", "http://127.0.0.1:25567");
    }
    String apiToken = environmentValue(environment, "ACHIEVEMENT_API_TOKEN");
    if (apiToken == null) {
      apiToken = config.getString("api-token", "replace-with-a-long-random-token");
    }
    long timeoutSeconds = config.getLong("timeout-seconds", 5);
    if (timeoutSeconds < 1 || timeoutSeconds > 60) {
      throw new IllegalArgumentException("HTTP timeout must be between 1 and 60 seconds.");
    }
    int retryAttempts = config.getInt("retry-attempts", 3);
    if (retryAttempts < 1 || retryAttempts > 10) {
      throw new IllegalArgumentException("Retry attempts must be between 1 and 10.");
    }
    return new PaperHookSettings(
      config.getBoolean("enabled", true),
      apiUrl,
      apiToken,
      config.getString("source", "paper_server"),
      Duration.ofSeconds(timeoutSeconds),
      retryAttempts
    );
  }

  private static String environmentValue(Map<String, String> environment, String name) {
    String value = environment.get(name);
    return value == null || value.isBlank() ? null : value.trim();
  }
}
