package dev.shtech.achievement.gui;

import dev.shtech.achievement.common.BadgeSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

final class TabBadgeSuffixFormatter {
  private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6}");

  private TabBadgeSuffixFormatter() {
  }

  static String format(List<BadgeSnapshot> badges) {
    if (badges == null || badges.isEmpty()) {
      return "";
    }
    StringBuilder suffix = new StringBuilder();
    badges.stream()
      .filter(badge -> badge != null && badge.symbol() != null && !badge.symbol().isBlank())
      .sorted(Comparator.comparingInt(BadgeSnapshot::slot))
      .forEach(badge -> suffix
        .append(' ')
        .append(color(badge.color()))
        .append(sanitize(badge.symbol()))
        .append("&r"));
    return suffix.toString();
  }

  private static String color(String color) {
    if (color == null || !COLOR.matcher(color).matches()) {
      return "&#FFFFFF";
    }
    return "&#" + color.substring(1).toUpperCase();
  }

  private static String sanitize(String symbol) {
    return symbol.replace("&", "").replace("§", "");
  }
}
