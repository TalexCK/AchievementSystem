package dev.shtech.achievement.common;

import java.util.Objects;

public record ProgressRequest(
  String playerUuid,
  String playerName,
  String categoryId,
  ProgressOperation operation,
  long amount,
  String eventId,
  String source
) {
  public ProgressRequest validated() {
    IdentifierValidator.uuid(playerUuid);
    String validName = IdentifierValidator.playerName(playerName);
    String validCategory = IdentifierValidator.identifier(categoryId, "categoryId");
    ProgressOperation validOperation = Objects.requireNonNull(operation, "operation");
    if ((validOperation == ProgressOperation.ADD && amount <= 0) || amount < 0) {
      throw new IllegalArgumentException("Invalid progress amount.");
    }
    if (amount > 9_000_000_000_000_000L) {
      throw new IllegalArgumentException("Progress amount is too large.");
    }
    String validEventId = IdentifierValidator.eventId(eventId);
    String validSource = IdentifierValidator.identifier(source, "source");
    return new ProgressRequest(
      playerUuid,
      validName,
      validCategory,
      validOperation,
      amount,
      validEventId,
      validSource
    );
  }
}

