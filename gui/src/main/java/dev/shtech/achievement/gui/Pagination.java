package dev.shtech.achievement.gui;

import java.util.List;

final class Pagination {
  private Pagination() {
  }

  static int pageCount(int totalEntries, int pageSize) {
    if (totalEntries < 0 || pageSize <= 0) {
      throw new IllegalArgumentException("Invalid pagination arguments.");
    }
    return Math.max(1, (totalEntries + pageSize - 1) / pageSize);
  }

  static int clampPage(int requestedPage, int totalEntries, int pageSize) {
    return Math.max(0, Math.min(requestedPage, pageCount(totalEntries, pageSize) - 1));
  }

  static <T> List<T> slice(List<T> entries, int requestedPage, int pageSize) {
    int page = clampPage(requestedPage, entries.size(), pageSize);
    int from = Math.min(page * pageSize, entries.size());
    int to = Math.min(from + pageSize, entries.size());
    return List.copyOf(entries.subList(from, to));
  }
}
