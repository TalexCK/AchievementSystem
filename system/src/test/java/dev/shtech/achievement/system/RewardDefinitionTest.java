package dev.shtech.achievement.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RewardDefinitionTest {
  @Test
  void parsesSupportedRewardTypes() {
    assertEquals(RewardType.ACHIEVEMENT_MESSAGE, RewardType.parse("achievement-message"));
    assertEquals(RewardType.MESSAGE, RewardType.parse("message"));
    assertEquals(RewardType.VELOCITY_COMMAND, RewardType.parse("velocity-command"));
    assertEquals(RewardType.PERMISSION, RewardType.parse("permission"));
    assertEquals(RewardType.GROUP, RewardType.parse("group"));
  }

  @Test
  void rejectsUnknownRewardTypes() {
    assertThrows(IllegalArgumentException.class, () -> RewardType.parse("item"));
  }
}
