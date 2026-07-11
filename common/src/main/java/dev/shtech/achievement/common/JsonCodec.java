package dev.shtech.achievement.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class JsonCodec {
  private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

  private JsonCodec() {
  }

  public static String write(Object value) {
    return GSON.toJson(value);
  }

  public static <T> T read(String value, Class<T> type) {
    return GSON.fromJson(value, type);
  }
}

