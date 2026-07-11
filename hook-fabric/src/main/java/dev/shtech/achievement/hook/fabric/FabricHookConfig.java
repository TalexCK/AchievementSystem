package dev.shtech.achievement.hook.fabric;

import dev.shtech.achievement.common.IdentifierValidator;
import dev.shtech.achievement.common.JsonCodec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

record FabricHookConfig(
  boolean enabled,
  String apiUrl,
  String apiToken,
  String source,
  long timeoutSeconds,
  int retryAttempts
) {
  private static final String DEFAULT_CONFIG = """
    {
      "enabled": true,
      "apiUrl": "http://127.0.0.1:25567",
      "apiToken": "replace-with-a-long-random-token",
      "source": "fabric_server",
      "timeoutSeconds": 5,
      "retryAttempts": 3
    }
    """;

  static FabricHookConfig loadOrCreate(Path path) throws IOException {
    if (Files.notExists(path)) {
      Files.createDirectories(path.getParent());
      Files.writeString(path, DEFAULT_CONFIG, StandardCharsets.UTF_8);
    }
    FabricHookConfig config = JsonCodec.read(
      Files.readString(path, StandardCharsets.UTF_8),
      FabricHookConfig.class
    );
    if (config == null) {
      throw new IllegalArgumentException("Achievement Hook configuration is empty.");
    }
    return config;
  }

  Settings settings(Map<String, String> environment) {
    String resolvedUrl = environmentValue(environment, "ACHIEVEMENT_API_URL", apiUrl);
    String resolvedToken = environmentValue(environment, "ACHIEVEMENT_API_TOKEN", apiToken);
    IdentifierValidator.loopbackHttpUri(resolvedUrl);
    IdentifierValidator.token(resolvedToken);
    String resolvedSource = IdentifierValidator.identifier(source, "source");
    if (timeoutSeconds < 1 || timeoutSeconds > 60) {
      throw new IllegalArgumentException("HTTP timeout must be between 1 and 60 seconds.");
    }
    if (retryAttempts < 1 || retryAttempts > 10) {
      throw new IllegalArgumentException("Retry attempts must be between 1 and 10.");
    }
    return new Settings(
      resolvedUrl.trim(),
      resolvedToken.trim(),
      resolvedSource,
      Duration.ofSeconds(timeoutSeconds),
      retryAttempts
    );
  }

  private static String environmentValue(
    Map<String, String> environment,
    String name,
    String fallback
  ) {
    String value = environment.get(name);
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  record Settings(
    String apiUrl,
    String apiToken,
    String source,
    Duration timeout,
    int retryAttempts
  ) {
  }
}
