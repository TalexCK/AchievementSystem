package dev.shtech.achievement.hook;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class AchievementHook {
  private static final AtomicReference<AchievementService> SERVICE = new AtomicReference<>();

  private AchievementHook() {
  }

  public static AchievementService service() {
    AchievementService service = SERVICE.get();
    if (service == null) {
      throw new IllegalStateException("AchievementHook is not initialized.");
    }
    return service;
  }

  public static Optional<AchievementService> optionalService() {
    return Optional.ofNullable(SERVICE.get());
  }

  public static boolean install(AchievementService service) {
    return SERVICE.compareAndSet(null, Objects.requireNonNull(service, "service"));
  }

  public static void uninstall(AchievementService service) {
    SERVICE.compareAndSet(service, null);
  }
}

