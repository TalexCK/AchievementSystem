package dev.shtech.achievement.hook.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FabricHookConfigTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  void createsSafeDisabledDefault() throws Exception {
    Path path = temporaryDirectory.resolve("config").resolve("achievement-hook.json");

    FabricHookConfig config = FabricHookConfig.loadOrCreate(path);

    assertTrue(config.enabled());
    assertTrue(Files.readString(path).contains("  \"apiUrl\""));
    assertThrows(IllegalArgumentException.class, () -> config.settings(Map.of()));
  }

  @Test
  void acceptsIndependentEnvironmentOverrides() throws Exception {
    Path path = temporaryDirectory.resolve("achievement-hook.json");
    FabricHookConfig config = FabricHookConfig.loadOrCreate(path);
    String token = "achievement-token-0123456789-abcdef";

    FabricHookConfig.Settings settings = config.settings(Map.of(
      "ACHIEVEMENT_API_URL", "http://127.0.0.1:25567",
      "ACHIEVEMENT_API_TOKEN", token
    ));

    assertEquals("http://127.0.0.1:25567", settings.apiUrl());
    assertEquals(token, settings.apiToken());
    assertEquals("fabric_server", settings.source());
  }
}
