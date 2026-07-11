package dev.shtech.achievement.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class TierSnapshotTest {
  @Test
  void preservesFirstUnlockerNameInJson() {
    TierSnapshot snapshot = new TierSnapshot(
      1,
      "Hidden",
      1,
      "#5555FF",
      false,
      4,
      "FirstPlayer",
      List.of()
    );

    TierSnapshot decoded = JsonCodec.read(JsonCodec.write(snapshot), TierSnapshot.class);

    assertEquals("FirstPlayer", decoded.firstUnlockerName());
  }

  @Test
  void acceptsLegacyJsonWithoutFirstUnlockerName() {
    TierSnapshot decoded = JsonCodec.read("""
      {
        "level": 1,
        "name": "Hidden",
        "threshold": 1,
        "color": "#5555FF",
        "unlocked": false,
        "unlockedPlayers": 0,
        "rewardDescriptions": []
      }
      """, TierSnapshot.class);

    assertNull(decoded.firstUnlockerName());
  }
}
