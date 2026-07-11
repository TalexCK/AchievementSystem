package dev.shtech.achievement.gui;

final class ProgressBar {
  private ProgressBar() {
  }

  static int filledSegments(long progress, long target, int segments) {
    if (progress < 0 || target < 0 || segments <= 0) {
      throw new IllegalArgumentException("Invalid progress bar arguments.");
    }
    if (target == 0 || progress >= target) {
      return segments;
    }
    return (int) Math.min(segments, progress * segments / target);
  }
}
