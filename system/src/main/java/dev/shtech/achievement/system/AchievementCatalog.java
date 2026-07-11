package dev.shtech.achievement.system;

import dev.shtech.achievement.common.IdentifierValidator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class AchievementCatalog {
  private final List<AchievementCategory> categories;
  private final Map<String, AchievementCategory> byId;

  public AchievementCatalog(List<AchievementCategory> categories) {
    List<AchievementCategory> ordered = categories.stream()
      .sorted(Comparator.comparingInt(AchievementCategory::order).thenComparing(AchievementCategory::id))
      .toList();
    Map<String, AchievementCategory> indexed = new LinkedHashMap<>();
    for (AchievementCategory category : ordered) {
      if (indexed.put(category.id(), category) != null) {
        throw new IllegalArgumentException("Duplicate achievement category: " + category.id());
      }
    }
    this.categories = ordered;
    this.byId = Map.copyOf(indexed);
  }

  public static AchievementCatalog load(Path dataDirectory) throws IOException {
    Path path = dataDirectory.resolve("achievements.yml");
    copyDefault(path, "/achievements.yml");
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setMaxAliasesForCollections(20);
    Object loaded;
    try (InputStream input = Files.newInputStream(path)) {
      loaded = new Yaml(new SafeConstructor(options)).load(input);
    }
    if (!(loaded instanceof Map<?, ?> root)) {
      throw new IllegalArgumentException("achievements.yml must contain a map.");
    }
    Object rawCategories = root.get("categories");
    if (!(rawCategories instanceof Map<?, ?> categoryMap)) {
      throw new IllegalArgumentException("achievements.yml must contain categories.");
    }
    List<AchievementCategory> categories = new ArrayList<>();
    for (Map.Entry<?, ?> entry : categoryMap.entrySet()) {
      String id = IdentifierValidator.identifier(String.valueOf(entry.getKey()), "categoryId");
      Map<?, ?> value = requireMap(entry.getValue(), "category " + id);
      if (!booleanValue(value, "enabled", true)) {
        continue;
      }
      categories.add(parseCategory(id, value));
    }
    return new AchievementCatalog(categories);
  }

  private static AchievementCategory parseCategory(String id, Map<?, ?> value) {
    List<?> rawTiers = requireList(value.get("tiers"), "tiers for " + id);
    List<AchievementTier> tiers = new ArrayList<>();
    for (int index = 0; index < rawTiers.size(); index++) {
      Map<?, ?> tier = requireMap(rawTiers.get(index), "tier " + (index + 1) + " for " + id);
      List<RewardDefinition> rewards = new ArrayList<>();
      for (Object rawReward : listValue(tier, "rewards")) {
        Map<?, ?> reward = requireMap(rawReward, "reward for " + id);
        rewards.add(new RewardDefinition(
          RewardType.parse(stringValue(reward, "type")),
          stringValue(reward, "value"),
          stringValue(reward, "description")
        ));
      }
      tiers.add(new AchievementTier(
        index + 1,
        stringValue(tier, "name"),
        longValue(tier, "threshold"),
        stringValue(tier, "color"),
        rewards
      ));
    }
    return new AchievementCategory(
      id,
      intValue(value, "order", 0),
      booleanValue(value, "hidden", false),
      booleanValue(value, "badge-enabled", true),
      stringValue(value, "display-name"),
      stringList(value, "description"),
      stringValue(value, "material"),
      stringValue(value, "symbol"),
      tiers
    );
  }

  public List<AchievementCategory> categories() {
    return categories;
  }

  public Optional<AchievementCategory> find(String id) {
    return Optional.ofNullable(byId.get(id));
  }

  public AchievementCategory require(String id) {
    return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown achievement category."));
  }

  private static void copyDefault(Path path, String resource) throws IOException {
    if (Files.exists(path)) {
      return;
    }
    Files.createDirectories(path.getParent());
    try (InputStream input = AchievementCatalog.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("Missing bundled resource: " + resource);
      }
      Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static Map<?, ?> requireMap(Object value, String field) {
    if (!(value instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(field + " must be a map.");
    }
    return map;
  }

  private static List<?> requireList(Object value, String field) {
    if (!(value instanceof List<?> list)) {
      throw new IllegalArgumentException(field + " must be a list.");
    }
    return list;
  }

  private static List<?> listValue(Map<?, ?> map, String field) {
    Object value = map.get(field);
    return value == null ? List.of() : requireList(value, field);
  }

  private static List<String> stringList(Map<?, ?> map, String field) {
    return listValue(map, field).stream().map(String::valueOf).toList();
  }

  private static String stringValue(Map<?, ?> map, String field) {
    Object value = map.get(field);
    if (value == null || String.valueOf(value).isBlank()) {
      throw new IllegalArgumentException(field + " is required.");
    }
    return String.valueOf(value).trim();
  }

  private static long longValue(Map<?, ?> map, String field) {
    Object value = map.get(field);
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException(field + " must be an integer.");
    }
  }

  private static int intValue(Map<?, ?> map, String field, int fallback) {
    Object value = map.get(field);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException(field + " must be an integer.");
    }
  }

  private static boolean booleanValue(Map<?, ?> map, String field, boolean fallback) {
    Object value = map.get(field);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Boolean bool) {
      return bool;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }
}
