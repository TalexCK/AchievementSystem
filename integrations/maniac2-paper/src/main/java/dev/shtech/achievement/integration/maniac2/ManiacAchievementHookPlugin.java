package dev.shtech.achievement.integration.maniac2;

import dev.shtech.achievement.hook.AchievementHook;
import dev.shtech.achievement.hook.AchievementService;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class ManiacAchievementHookPlugin extends JavaPlugin implements Listener {
  private static final String CATEGORY_ID = "maniac_2_everything";
  private static final Set<String> PLAYER_TEAMS = Set.of("peaceful", "maniac", "police");
  private final Set<UUID> pending = new HashSet<>();
  private final Set<UUID> unavailableWarnings = new HashSet<>();
  private NamespacedKey roundKey;
  private NamespacedKey keyMaskKey;
  private NamespacedKey itemMaskKey;
  private NamespacedKey basementKeysKey;
  private NamespacedKey fuelCansKey;
  private NamespacedKey itemTokensKey;
  private NamespacedKey submittedKey;
  private String instanceId;

  @Override
  public void onEnable() {
    roundKey = new NamespacedKey(this, "round");
    keyMaskKey = new NamespacedKey(this, "key_mask");
    itemMaskKey = new NamespacedKey(this, "item_mask");
    basementKeysKey = new NamespacedKey(this, "basement_keys");
    fuelCansKey = new NamespacedKey(this, "fuel_cans");
    itemTokensKey = new NamespacedKey(this, "item_tokens");
    submittedKey = new NamespacedKey(this, "submitted");
    instanceId = normalizeInstance(System.getenv("SCHEDULER_INSTANCE_ID"));
    getServer().getPluginManager().registerEvents(this, this);
    Bukkit.getScheduler().runTaskTimer(this, this::scanOnlinePlayers, 10L, 10L);
    getLogger().info("Maniac 2 achievement integration enabled for instance " + instanceId + ".");
  }

  @Override
  public void onDisable() {
    pending.clear();
    unavailableWarnings.clear();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPickup(EntityPickupItemEvent event) {
    if (event.getEntity() instanceof Player player) {
      scheduleScan(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onInventoryClick(InventoryClickEvent event) {
    if (event.getWhoClicked() instanceof Player player) {
      scheduleScan(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onInventoryDrag(InventoryDragEvent event) {
    if (event.getWhoClicked() instanceof Player player) {
      scheduleScan(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    scheduleScan(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    UUID playerUuid = event.getPlayer().getUniqueId();
    pending.remove(playerUuid);
    unavailableWarnings.remove(playerUuid);
  }

  private void scheduleScan(Player player) {
    Bukkit.getScheduler().runTask(this, () -> {
      if (player.isOnline()) {
        scan(player);
      }
    });
  }

  private void scanOnlinePlayers() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      scan(player);
    }
  }

  private void scan(Player player) {
    String round = currentRound(player);
    PersistentDataContainer data = player.getPersistentDataContainer();
    if (round == null) {
      clearState(player, data);
      return;
    }
    String storedRound = data.get(roundKey, PersistentDataType.STRING);
    if (!round.equals(storedRound)) {
      data.set(roundKey, PersistentDataType.STRING, round);
      data.set(keyMaskKey, PersistentDataType.INTEGER, 0);
      data.set(itemMaskKey, PersistentDataType.INTEGER, 0);
      data.remove(basementKeysKey);
      data.remove(fuelCansKey);
      data.remove(submittedKey);
      pending.remove(player.getUniqueId());
      unavailableWarnings.remove(player.getUniqueId());
    }
    int mask = data.getOrDefault(keyMaskKey, PersistentDataType.INTEGER, 0);
    int itemMask = data.getOrDefault(itemMaskKey, PersistentDataType.INTEGER, 0);
    Set<String> basementKeys = decodeTokens(data.get(basementKeysKey, PersistentDataType.STRING));
    Set<String> fuelCans = decodeTokens(data.get(fuelCansKey, PersistentDataType.STRING));
    InventoryProgress progress = inventoryProgress(
      player,
      mask,
      itemMask,
      basementKeys,
      fuelCans
    );
    if (progress.keyMask() != mask) {
      data.set(keyMaskKey, PersistentDataType.INTEGER, progress.keyMask());
    }
    if (progress.itemMask() != itemMask) {
      data.set(itemMaskKey, PersistentDataType.INTEGER, progress.itemMask());
    }
    if (!progress.basementKeys().equals(basementKeys)) {
      data.set(
        basementKeysKey,
        PersistentDataType.STRING,
        String.join(",", progress.basementKeys())
      );
    }
    if (!progress.fuelCans().equals(fuelCans)) {
      data.set(
        fuelCansKey,
        PersistentDataType.STRING,
        String.join(",", progress.fuelCans())
      );
    }
    if (!ManiacCollectionProgress.complete(
      progress.keyMask(),
      progress.basementKeys().size(),
      progress.fuelCans().size(),
      progress.itemMask()
    )
      || data.has(submittedKey, PersistentDataType.BYTE)
      || !pending.add(player.getUniqueId())) {
      return;
    }
    AchievementService service = AchievementHook.optionalService().orElse(null);
    if (service == null) {
      pending.remove(player.getUniqueId());
      if (unavailableWarnings.add(player.getUniqueId())) {
        getLogger().warning("Achievement Hook is unavailable; the Maniac 2 achievement will retry.");
      }
      return;
    }
    unavailableWarnings.remove(player.getUniqueId());
    String eventId = "maniac2:" + round + ":" + player.getUniqueId() + ":everything";
    service.max(
      player.getUniqueId(),
      player.getName(),
      CATEGORY_ID,
      1,
      eventId
    ).whenComplete((response, error) -> Bukkit.getScheduler().runTask(this, () -> {
      pending.remove(player.getUniqueId());
      if (error != null) {
        getLogger().warning(
          "Failed to report the Maniac 2 achievement for " + player.getName() + ": "
            + rootMessage(error)
        );
        return;
      }
      if (!response.accepted()) {
        String message = response.error();
        getLogger().warning(
          "The Maniac 2 achievement was rejected for " + player.getName() + ": "
            + (message == null || message.isBlank() ? "Unknown rejection." : message)
        );
        return;
      }
      if (player.isOnline() && round.equals(currentRound(player))) {
        player.getPersistentDataContainer().set(submittedKey, PersistentDataType.BYTE, (byte) 1);
      }
    }));
  }

  private void clearState(Player player, PersistentDataContainer data) {
    if (data.has(roundKey)
      || data.has(keyMaskKey)
      || data.has(itemMaskKey)
      || data.has(basementKeysKey)
      || data.has(fuelCansKey)
      || data.has(submittedKey)) {
      data.remove(roundKey);
      data.remove(keyMaskKey);
      data.remove(itemMaskKey);
      data.remove(basementKeysKey);
      data.remove(fuelCansKey);
      data.remove(submittedKey);
    }
    pending.remove(player.getUniqueId());
    unavailableWarnings.remove(player.getUniqueId());
  }

  private InventoryProgress inventoryProgress(
    Player player,
    int initialKeyMask,
    int initialItemMask,
    Set<String> initialBasementKeys,
    Set<String> initialFuelCans
  ) {
    int keyMask = initialKeyMask;
    int itemMask = initialItemMask;
    Set<String> basementKeys = new LinkedHashSet<>(initialBasementKeys);
    Set<String> fuelCans = new LinkedHashSet<>(initialFuelCans);
    Set<String> basementInventoryTokens = new HashSet<>();
    Set<String> fuelInventoryTokens = new HashSet<>();
    for (ItemStack item : player.getInventory().getContents()) {
      if (item == null || item.getType().isAir()) {
        continue;
      }
      ItemMeta meta = item.getItemMeta();
      NamespacedKey model = meta.getItemModel();
      if (model == null
        || !model.getNamespace().equals(NamespacedKey.MINECRAFT)) {
        continue;
      }
      String modelName = model.getKey();
      keyMask = ManiacKeyMask.add(keyMask, modelName);
      itemMask = ManiacCollectionProgress.addItem(itemMask, modelName);
      if (!modelName.equals("kluch_8") && !modelName.equals("kanistra")) {
        continue;
      }
      boolean basement = modelName.equals("kluch_8");
      Set<String> itemTokens = normalizeItemTokens(
        item,
        meta,
        basement ? basementInventoryTokens : fuelInventoryTokens,
        basement ? 2 : 6
      );
      if (modelName.equals("kluch_8")) {
        basementKeys.addAll(itemTokens);
      } else {
        fuelCans.addAll(itemTokens);
      }
    }
    return new InventoryProgress(
      keyMask,
      itemMask,
      Set.copyOf(basementKeys),
      Set.copyOf(fuelCans)
    );
  }

  private Set<String> normalizeItemTokens(
    ItemStack item,
    ItemMeta meta,
    Set<String> inventoryTokens,
    int maximumTokens
  ) {
    Set<String> existing = decodeTokens(
      meta.getPersistentDataContainer().get(itemTokensKey, PersistentDataType.STRING)
    );
    int expected = Math.min(maximumTokens, Math.max(1, item.getAmount()));
    Set<String> normalized = ManiacTokenNormalizer.normalize(
      existing,
      inventoryTokens,
      expected,
      () -> UUID.randomUUID().toString()
    );
    meta.getPersistentDataContainer().set(
      itemTokensKey,
      PersistentDataType.STRING,
      String.join(",", normalized)
    );
    item.setItemMeta(meta);
    return normalized;
  }

  private static Set<String> decodeTokens(String encoded) {
    Set<String> tokens = new LinkedHashSet<>();
    if (encoded == null || encoded.isBlank()) {
      return tokens;
    }
    for (String token : encoded.split(",")) {
      if (!token.isBlank()) {
        tokens.add(token);
      }
    }
    return tokens;
  }

  private String currentRound(Player player) {
    Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    Objective globalData = scoreboard.getObjective("global_data");
    Objective gameCount = scoreboard.getObjective("game_count");
    if (globalData == null || gameCount == null) {
      return null;
    }
    if (!globalData.getScore("game_state").isScoreSet()
      || globalData.getScore("game_state").getScore() != 1
      || !gameCount.getScore("total").isScoreSet()) {
      return null;
    }
    Team team = scoreboard.getEntryTeam(player.getName());
    if (team == null || !PLAYER_TEAMS.contains(team.getName())) {
      return null;
    }
    return instanceId + ":" + gameCount.getScore("total").getScore();
  }

  private static String normalizeInstance(String value) {
    String normalized = value == null || value.isBlank()
      ? "standalone"
      : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    return normalized.length() <= 48 ? normalized : normalized.substring(0, 48);
  }

  private static String rootMessage(Throwable error) {
    Throwable current = error;
    while (current instanceof CompletionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }

  private record InventoryProgress(
    int keyMask,
    int itemMask,
    Set<String> basementKeys,
    Set<String> fuelCans
  ) {
  }
}
