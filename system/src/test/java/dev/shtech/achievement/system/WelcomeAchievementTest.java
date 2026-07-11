package dev.shtech.achievement.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.shtech.achievement.common.ProgressOperation;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WelcomeAchievementTest {
  @Test
  void grantsOnlyAfterTheFirstBackendConnection() {
    assertTrue(WelcomeAchievement.shouldGrant(Optional.empty()));
    assertFalse(WelcomeAchievement.shouldGrant(Optional.of("lobby")));
  }

  @Test
  void createsAnIdempotentStateAssertion() {
    UUID playerUuid = UUID.randomUUID();
    var request = WelcomeAchievement.request(playerUuid, "WelcomeTest");

    assertEquals(playerUuid.toString(), request.playerUuid());
    assertEquals("WelcomeTest", request.playerName());
    assertEquals("welcome", request.categoryId());
    assertEquals(ProgressOperation.MAX, request.operation());
    assertEquals(1, request.amount());
    assertEquals("welcome:first-login", request.eventId());
    assertEquals("achievement_system", request.source());
  }
}
