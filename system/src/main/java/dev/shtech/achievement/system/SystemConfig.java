package dev.shtech.achievement.system;

import dev.shtech.achievement.common.IdentifierValidator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public record SystemConfig(
  String apiHost,
  int apiPort,
  String apiToken,
  int maximumBodyBytes,
  String databaseUrl,
  String databaseUser,
  String databasePassword,
  int databasePoolSize,
  int maximumSelectedBadges,
  int suffixPriority,
  Duration rewardPollInterval,
  int rewardBatchSize
) {
  public static SystemConfig load(Path dataDirectory) throws IOException {
    Path path = dataDirectory.resolve("config.yml");
    copyDefault(path);
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    Object loaded;
    try (InputStream input = Files.newInputStream(path)) {
      loaded = new Yaml(new SafeConstructor(options)).load(input);
    }
    Map<?, ?> root = requireMap(loaded, "config.yml");
    Map<?, ?> api = requireMap(root.get("api"), "api");
    Map<?, ?> database = requireMap(root.get("database"), "database");
    Map<?, ?> badges = requireMap(root.get("badges"), "badges");
    Map<?, ?> rewards = requireMap(root.get("rewards"), "rewards");
    String token = environment("ACHIEVEMENT_API_TOKEN", stringValue(api, "token"));
    String url = environment("ACHIEVEMENT_DATABASE_URL", stringValue(database, "url"));
    String user = environment("ACHIEVEMENT_DATABASE_USER", stringValue(database, "user"));
    String password = environment(
      "ACHIEVEMENT_DATABASE_PASSWORD",
      stringValue(database, "password")
    );
    SystemConfig config = new SystemConfig(
      stringValue(api, "host"),
      intValue(api, "port"),
      IdentifierValidator.token(token),
      intValue(api, "maximum-body-bytes"),
      url,
      user,
      password,
      intValue(database, "pool-size"),
      intValue(badges, "maximum-selected"),
      intValue(badges, "luckperms-suffix-priority"),
      Duration.ofSeconds(intValue(rewards, "poll-seconds")),
      intValue(rewards, "batch-size")
    );
    config.validate();
    return config;
  }

  private void validate() {
    if (!(apiHost.equals("127.0.0.1") || apiHost.equals("localhost") || apiHost.equals("::1"))) {
      throw new IllegalArgumentException("Achievement API must bind to a loopback address.");
    }
    if (apiPort < 1 || apiPort > 65_535) {
      throw new IllegalArgumentException("Achievement API port is invalid.");
    }
    if (maximumBodyBytes < 1_024 || maximumBodyBytes > 1_048_576) {
      throw new IllegalArgumentException("Achievement API body limit is invalid.");
    }
    if (!databaseUrl.startsWith("jdbc:postgresql://")) {
      throw new IllegalArgumentException("Achievement database URL must use PostgreSQL.");
    }
    if (databaseUser.isBlank()
      || databasePassword.isBlank()
      || databasePassword.equals("replace-with-a-database-password")) {
      throw new IllegalArgumentException("Achievement database credentials are required.");
    }
    if (databasePoolSize < 1 || databasePoolSize > 32) {
      throw new IllegalArgumentException("Achievement database pool size is invalid.");
    }
    if (maximumSelectedBadges < 1 || maximumSelectedBadges > 3) {
      throw new IllegalArgumentException("Maximum selected badges must be between 1 and 3.");
    }
    if (suffixPriority < 1 || suffixPriority > 10_000) {
      throw new IllegalArgumentException("LuckPerms suffix priority is invalid.");
    }
    if (rewardPollInterval.isZero() || rewardPollInterval.isNegative()) {
      throw new IllegalArgumentException("Reward poll interval must be positive.");
    }
    if (rewardBatchSize < 1 || rewardBatchSize > 500) {
      throw new IllegalArgumentException("Reward batch size is invalid.");
    }
  }

  private static void copyDefault(Path path) throws IOException {
    if (Files.exists(path)) {
      return;
    }
    Files.createDirectories(path.getParent());
    try (InputStream input = SystemConfig.class.getResourceAsStream("/config.yml")) {
      if (input == null) {
        throw new IOException("Missing bundled config.yml.");
      }
      Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static Map<?, ?> requireMap(Object value, String field) {
    if (!(value instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(field + " must be a map.");
    }
    return map;
  }

  private static String stringValue(Map<?, ?> map, String field) {
    Object value = map.get(field);
    if (value == null) {
      throw new IllegalArgumentException(field + " is required.");
    }
    return String.valueOf(value).trim();
  }

  private static int intValue(Map<?, ?> map, String field) {
    Object value = map.get(field);
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException(field + " must be an integer.");
    }
  }

  private static String environment(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
