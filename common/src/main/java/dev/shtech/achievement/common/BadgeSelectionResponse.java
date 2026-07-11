package dev.shtech.achievement.common;

import java.util.List;

public record BadgeSelectionResponse(boolean accepted, List<BadgeSnapshot> badges, String error) {
  public BadgeSelectionResponse {
    badges = badges == null ? List.of() : List.copyOf(badges);
  }

  public static BadgeSelectionResponse failure(String error) {
    return new BadgeSelectionResponse(false, List.of(), error);
  }
}

