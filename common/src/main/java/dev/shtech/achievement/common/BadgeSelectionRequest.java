package dev.shtech.achievement.common;

import java.util.LinkedHashSet;
import java.util.List;

public record BadgeSelectionRequest(List<String> categoryIds) {
  public BadgeSelectionRequest {
    categoryIds = categoryIds == null ? List.of() : List.copyOf(categoryIds);
  }

  public BadgeSelectionRequest validated(int maximum) {
    if (categoryIds.size() > maximum) {
      throw new IllegalArgumentException("Too many selected badges.");
    }
    List<String> normalized = categoryIds.stream()
      .map(value -> IdentifierValidator.identifier(value, "categoryId"))
      .toList();
    if (new LinkedHashSet<>(normalized).size() != normalized.size()) {
      throw new IllegalArgumentException("Selected badges must be unique.");
    }
    return new BadgeSelectionRequest(normalized);
  }
}

