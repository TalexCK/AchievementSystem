package dev.shtech.achievement.system;

import dev.shtech.achievement.common.BadgeSnapshot;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.SuffixNode;

public final class LuckPermsBadgeService {
  private final LuckPerms luckPerms;
  private final int suffixPriority;

  public LuckPermsBadgeService(LuckPerms luckPerms, int suffixPriority) {
    this.luckPerms = luckPerms;
    this.suffixPriority = suffixPriority;
  }

  public CompletableFuture<Void> apply(UUID playerUuid, List<BadgeSnapshot> badges) {
    String suffix = buildSuffix(badges);
    return luckPerms.getUserManager().loadUser(playerUuid).thenCompose(user -> {
      replaceSuffix(user, suffix);
      return luckPerms.getUserManager().saveUser(user);
    });
  }

  public String buildSuffix(List<BadgeSnapshot> badges) {
    StringBuilder suffix = new StringBuilder();
    for (BadgeSnapshot badge : badges) {
      suffix.append(' ')
        .append("&#")
        .append(badge.color().substring(1))
        .append(badge.symbol())
        .append("&r");
    }
    return suffix.toString();
  }

  private void replaceSuffix(User user, String suffix) {
    user.data().clear(node -> node instanceof SuffixNode suffixNode
      && suffixNode.getPriority() == suffixPriority);
    if (!suffix.isEmpty()) {
      user.data().add(SuffixNode.builder(suffix, suffixPriority).build());
    }
  }
}

