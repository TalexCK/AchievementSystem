package dev.shtech.achievement.hook.paper;

import dev.shtech.achievement.common.IdentifierValidator;
import dev.shtech.achievement.common.ProgressResponse;
import dev.shtech.achievement.hook.AchievementService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

record HookCommandRequest(
  Operation operation,
  String player,
  String categoryId,
  long amount,
  String eventId
) {
  enum Operation {
    ADD,
    SET,
    MAX
  }

  static HookCommandRequest parse(String[] arguments) {
    if (arguments.length < 4 || arguments.length > 5) {
      throw new IllegalArgumentException("Invalid command arguments.");
    }
    Operation operation;
    try {
      operation = Operation.valueOf(arguments[0].toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException("Operation must be add, set, or max.");
    }
    String player = arguments[1].trim();
    if (player.isEmpty()) {
      throw new IllegalArgumentException("Player is required.");
    }
    String categoryId = IdentifierValidator.identifier(arguments[2], "categoryId");
    long amount;
    try {
      amount = Long.parseLong(arguments[3]);
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException("Amount must be an integer.");
    }
    if ((operation == Operation.ADD && amount <= 0) || amount < 0) {
      throw new IllegalArgumentException("Invalid progress amount.");
    }
    if (amount > 9_000_000_000_000_000L) {
      throw new IllegalArgumentException("Progress amount is too large.");
    }
    String eventId = arguments.length == 5
      ? IdentifierValidator.eventId(arguments[4])
      : null;
    return new HookCommandRequest(operation, player, categoryId, amount, eventId);
  }

  CompletableFuture<ProgressResponse> submit(
    AchievementService service,
    UUID playerUuid,
    String playerName
  ) {
    return switch (operation) {
      case ADD -> service.add(playerUuid, playerName, categoryId, amount, eventId);
      case SET -> service.set(playerUuid, playerName, categoryId, amount, eventId);
      case MAX -> service.max(playerUuid, playerName, categoryId, amount, eventId);
    };
  }
}
