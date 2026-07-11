package dev.shtech.achievement.gui;

import dev.shtech.achievement.common.BadgeSnapshot;
import dev.shtech.achievement.common.CategorySnapshot;
import dev.shtech.achievement.common.PlayerSnapshot;
import dev.shtech.achievement.common.TierSnapshot;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

final class GuiItems {
  private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.CHINA);

  private GuiItems() {
  }

  static ItemStack border(boolean alternate) {
    Material material = alternate
      ? Material.GRAY_STAINED_GLASS_PANE
      : Material.BLACK_STAINED_GLASS_PANE;
    return item(material, Component.empty(), List.of(), false);
  }

  static ItemStack playerSummary(Player player, PlayerSnapshot snapshot) {
    ItemStack item = new ItemStack(Material.PLAYER_HEAD);
    SkullMeta meta = (SkullMeta) item.getItemMeta();
    meta.setOwningPlayer(player);
    meta.displayName(component(player.getName() + " 的成就", NamedTextColor.GOLD));
    meta.lore(normalize(List.of(
      component("已解锁等级：", NamedTextColor.GRAY)
        .append(component(
          snapshot.unlockedTiers() + " / " + snapshot.totalTiers(),
          NamedTextColor.AQUA
        )),
      component("展示徽章：", NamedTextColor.GRAY)
        .append(component(snapshot.selectedBadges().size() + " / 3", NamedTextColor.LIGHT_PURPLE)),
      Component.empty(),
      component("选择一个分类查看完整进度", NamedTextColor.YELLOW)
    )));
    item.setItemMeta(meta);
    return item;
  }

  static ItemStack category(CategorySnapshot category) {
    List<Component> lore = new ArrayList<>();
    category.description().forEach(line -> lore.add(component(line, NamedTextColor.GRAY)));
    if (!lore.isEmpty()) {
      lore.add(Component.empty());
    }
    lore.add(component("进度：", NamedTextColor.GRAY)
      .append(component(number(category.progress()), NamedTextColor.AQUA)));
    lore.add(component("已获取人数：", NamedTextColor.GRAY)
      .append(component(number(category.unlockedPlayers()), NamedTextColor.GREEN)));
    addFirstUnlocker(lore, category);
    lore.add(component("当前等级：", NamedTextColor.GRAY)
      .append(component(
        category.currentTier() + " / " + category.tiers().size(),
        color(category),
        NamedTextColor.WHITE
      )));
    if (category.currentTier() < category.tiers().size() && category.nextThreshold() > 0) {
      lore.add(component("下一级目标：", NamedTextColor.GRAY)
        .append(component(number(category.nextThreshold()), NamedTextColor.YELLOW)));
    }
    if (category.selected()) {
      lore.add(component("✓ 正在展示", NamedTextColor.GREEN));
    }
    lore.add(Component.empty());
    lore.add(component("左键查看详情", NamedTextColor.YELLOW));
    return item(
      material(category.material()),
      component(category.symbol() + " " + category.displayName(), color(category), NamedTextColor.GOLD),
      lore,
      category.selected()
    );
  }

  static ItemStack categoryHeader(CategorySnapshot category) {
    List<Component> lore = new ArrayList<>();
    category.description().forEach(line -> lore.add(component(line, NamedTextColor.GRAY)));
    lore.add(Component.empty());
    lore.add(component("累计进度：", NamedTextColor.GRAY)
      .append(component(number(category.progress()), NamedTextColor.AQUA)));
    lore.add(component("已获取人数：", NamedTextColor.GRAY)
      .append(component(number(category.unlockedPlayers()), NamedTextColor.GREEN)));
    addFirstUnlocker(lore, category);
    lore.add(component("当前等级：", NamedTextColor.GRAY)
      .append(component(
        category.currentTier() + " / " + category.tiers().size(),
        color(category),
        NamedTextColor.WHITE
      )));
    return item(
      material(category.material()),
      component(category.symbol() + " " + category.displayName(), color(category), NamedTextColor.GOLD),
      lore,
      category.selected()
    );
  }

  static ItemStack tier(TierSnapshot tier, boolean current, boolean hidden) {
    Material material = current
      ? Material.NETHER_STAR
      : tier.unlocked() ? Material.FIREWORK_STAR : Material.GUNPOWDER;
    List<Component> lore = new ArrayList<>();
    lore.add(component("目标：", NamedTextColor.GRAY)
      .append(component(number(tier.threshold()), NamedTextColor.YELLOW)));
    lore.add(component("全服获取人数：", NamedTextColor.GRAY)
      .append(component(number(tier.unlockedPlayers()), NamedTextColor.AQUA)));
    if (hidden) {
      lore.add(firstUnlockerLine(tier.firstUnlockerName()));
    }
    lore.add(component(
      tier.unlocked() ? "✓ 已解锁" : "✗ 尚未解锁",
      tier.unlocked() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY
    ));
    if (current) {
      lore.add(component("当前展示颜色", NamedTextColor.LIGHT_PURPLE));
    }
    if (!tier.rewardDescriptions().isEmpty()) {
      lore.add(Component.empty());
      lore.add(component("奖励", NamedTextColor.GOLD));
      tier.rewardDescriptions().forEach(reward -> lore.add(
        component("• " + reward, NamedTextColor.GRAY)
      ));
    }
    return item(
      material,
      component("等级 " + tier.level() + " · " + tier.name(), tier.color(), NamedTextColor.WHITE),
      lore,
      tier.unlocked()
    );
  }

  static ItemStack missingTier(int level) {
    return item(
      Material.LIGHT_GRAY_DYE,
      component("等级 " + level + " · 尚未配置", NamedTextColor.DARK_GRAY),
      List.of(component("该等级暂未开放", NamedTextColor.GRAY)),
      false
    );
  }

  static ItemStack progressSegment(long progress, long target, boolean filled) {
    return item(
      filled ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
      component(
        "总进度 " + number(progress) + " / " + number(target),
        filled ? NamedTextColor.GREEN : NamedTextColor.GRAY
      ),
      List.of(),
      false
    );
  }

  static ItemStack badge(CategorySnapshot category, int selectedSlot) {
    boolean selected = selectedSlot > 0;
    List<Component> lore = new ArrayList<>();
    lore.add(component("已解锁等级：", NamedTextColor.GRAY)
      .append(component(
        category.currentTier() + " / " + category.tiers().size(),
        color(category),
        NamedTextColor.WHITE
      )));
    lore.add(component("当前图案：", NamedTextColor.GRAY)
      .append(component(category.symbol(), color(category), NamedTextColor.WHITE)));
    if (selected) {
      lore.add(component("展示位置：" + selectedSlot, NamedTextColor.LIGHT_PURPLE));
    }
    lore.add(Component.empty());
    lore.add(component(
      selected ? "点击取消展示" : "点击加入展示",
      selected ? NamedTextColor.RED : NamedTextColor.GREEN
    ));
    return item(
      material(category.material()),
      component(category.symbol() + " " + category.displayName(), color(category), NamedTextColor.GOLD),
      lore,
      selected
    );
  }

  static ItemStack badgeSummary(List<BadgeSnapshot> badges) {
    List<Component> lore = new ArrayList<>();
    if (badges.isEmpty()) {
      lore.add(component("尚未选择展示徽章", NamedTextColor.GRAY));
    } else {
      badges.stream()
        .sorted(Comparator.comparingInt(BadgeSnapshot::slot))
        .forEach(badge -> lore.add(
          component(badge.slot() + ". ", NamedTextColor.DARK_GRAY)
            .append(component(
              badge.symbol() + " " + badge.displayName(),
              badge.color(),
              NamedTextColor.WHITE
            ))
        ));
    }
    lore.add(Component.empty());
    lore.add(component("最多同时展示 3 个", NamedTextColor.YELLOW));
    lore.add(component("修改后由成就服务确认", NamedTextColor.GRAY));
    return item(
      Material.NAME_TAG,
      component("当前展示 · " + badges.size() + " / 3", NamedTextColor.LIGHT_PURPLE),
      lore,
      !badges.isEmpty()
    );
  }

  static ItemStack previous(int page) {
    return item(
      Material.ARROW,
      component("上一页", NamedTextColor.YELLOW),
      List.of(component("当前第 " + (page + 1) + " 页", NamedTextColor.GRAY)),
      false
    );
  }

  static ItemStack next(int page, int pageCount) {
    return item(
      Material.ARROW,
      component("下一页", NamedTextColor.YELLOW),
      List.of(component("共 " + pageCount + " 页", NamedTextColor.GRAY)),
      false
    );
  }

  static ItemStack back() {
    return item(
      Material.SPECTRAL_ARROW,
      component("返回成就总览", NamedTextColor.YELLOW),
      List.of(),
      false
    );
  }

  static ItemStack close() {
    return item(
      Material.BARRIER,
      component("关闭", NamedTextColor.RED),
      List.of(),
      false
    );
  }

  static ItemStack refresh() {
    return item(
      Material.SUNFLOWER,
      component("刷新", NamedTextColor.AQUA),
      List.of(component("从成就服务重新获取数据", NamedTextColor.GRAY)),
      false
    );
  }

  static ItemStack badges() {
    return item(
      Material.NAME_TAG,
      component("选择展示徽章", NamedTextColor.LIGHT_PURPLE),
      List.of(
        component("仅能选择已经解锁的分类", NamedTextColor.GRAY),
        component("最多同时展示 3 个", NamedTextColor.GRAY)
      ),
      true
    );
  }

  static ItemStack pending() {
    return item(
      Material.CLOCK,
      component("正在保存选择…", NamedTextColor.YELLOW),
      List.of(component("请稍候", NamedTextColor.GRAY)),
      true
    );
  }

  private static ItemStack item(
    Material material,
    Component name,
    List<Component> lore,
    boolean glint
  ) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(normalize(name));
    meta.lore(normalize(lore));
    meta.setEnchantmentGlintOverride(glint);
    item.setItemMeta(meta);
    return item;
  }

  private static List<Component> normalize(List<Component> components) {
    return components.stream().map(GuiItems::normalize).toList();
  }

  private static Component normalize(Component component) {
    return component.decoration(TextDecoration.ITALIC, false);
  }

  private static Component component(String text, NamedTextColor color) {
    return normalize(Component.text(text, color));
  }

  private static Component component(String text, String color, NamedTextColor fallback) {
    return normalize(Component.text(text, color(color, fallback)));
  }

  private static Component component(String text, TextColor color, NamedTextColor fallback) {
    return normalize(Component.text(text, color == null ? fallback : color));
  }

  private static TextColor color(CategorySnapshot category) {
    return category.tiers().stream()
      .filter(tier -> tier.level() == category.currentTier())
      .findFirst()
      .map(TierSnapshot::color)
      .map(value -> color(value, NamedTextColor.GRAY))
      .orElse(NamedTextColor.GRAY);
  }

  private static void addFirstUnlocker(List<Component> lore, CategorySnapshot category) {
    if (!category.hidden()) {
      return;
    }
    String firstUnlocker = category.tiers().stream()
      .filter(tier -> tier.level() == 1)
      .findFirst()
      .map(TierSnapshot::firstUnlockerName)
      .orElse(null);
    lore.add(firstUnlockerLine(firstUnlocker));
  }

  private static Component firstUnlockerLine(String playerName) {
    String displayName = firstUnlockerName(playerName);
    boolean unknown = displayName.equals("???");
    return component("首位获取者：", NamedTextColor.GRAY)
      .append(component(
        displayName,
        unknown ? NamedTextColor.DARK_GRAY : NamedTextColor.GOLD
      ));
  }

  static String firstUnlockerName(String playerName) {
    return playerName == null || playerName.isBlank() ? "???" : playerName;
  }

  private static TextColor color(String value, NamedTextColor fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    TextColor named = NamedTextColor.NAMES.value(normalized);
    if (named != null) {
      return named;
    }
    TextColor hex = TextColor.fromHexString(normalized.startsWith("#") ? normalized : "#" + normalized);
    return hex == null ? fallback : hex;
  }

  private static Material material(String value) {
    Material material = value == null
      ? null
      : Material.matchMaterial(value.trim().toUpperCase(Locale.ROOT));
    if (material == null || material.isAir() || !material.isItem()) {
      return Material.BOOK;
    }
    return material;
  }

  private static String number(long value) {
    return NUMBER_FORMAT.format(value);
  }
}
