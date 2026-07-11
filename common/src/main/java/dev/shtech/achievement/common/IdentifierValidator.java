package dev.shtech.achievement.common;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class IdentifierValidator {
  private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");
  private static final Pattern EVENT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:/-]{0,127}");

  private IdentifierValidator() {
  }

  public static String identifier(String value, String field) {
    String normalized = Objects.requireNonNull(value, field).trim().toLowerCase(Locale.ROOT);
    if (!IDENTIFIER.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Invalid " + field + ".");
    }
    return normalized;
  }

  public static String eventId(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim();
    if (!EVENT_ID.matcher(normalized).matches()) {
      throw new IllegalArgumentException("Invalid eventId.");
    }
    return normalized;
  }

  public static String playerName(String value) {
    String normalized = Objects.requireNonNull(value, "playerName").trim();
    if (normalized.isEmpty() || normalized.length() > 32) {
      throw new IllegalArgumentException("Invalid playerName.");
    }
    for (int index = 0; index < normalized.length(); index++) {
      if (Character.isISOControl(normalized.charAt(index))) {
        throw new IllegalArgumentException("Invalid playerName.");
      }
    }
    return normalized;
  }

  public static UUID uuid(String value) {
    try {
      return UUID.fromString(Objects.requireNonNull(value, "playerUuid"));
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException("Invalid playerUuid.");
    }
  }

  public static URI loopbackHttpUri(String value) {
    URI uri = URI.create(Objects.requireNonNull(value, "apiUrl").trim());
    String host = uri.getHost();
    boolean loopback = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)
      || "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host);
    if (!"http".equalsIgnoreCase(uri.getScheme()) || !loopback || uri.getUserInfo() != null) {
      throw new IllegalArgumentException("Achievement API URL must use loopback HTTP.");
    }
    return uri;
  }

  public static String token(String value) {
    String normalized = Objects.requireNonNull(value, "token").trim();
    if (normalized.length() < 32 || normalized.equals("replace-with-a-long-random-token")) {
      throw new IllegalArgumentException("Achievement API token must contain at least 32 characters.");
    }
    return normalized;
  }
}

