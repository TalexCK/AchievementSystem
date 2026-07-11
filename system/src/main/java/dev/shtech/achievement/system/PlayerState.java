package dev.shtech.achievement.system;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlayerState(
  UUID playerUuid,
  String playerName,
  Map<String, Long> progress,
  List<String> selectedCategories
) {
  public PlayerState {
    progress = Map.copyOf(progress);
    selectedCategories = List.copyOf(selectedCategories);
  }
}

