# Achievement Platform

独立于 Scheduler 的群组服成就系统。Velocity 插件是唯一的数据与奖励权威，Lobby 插件负责
展示和徽章选择，Paper/Fabric Hook 为游戏插件和模组提供统一的进度上报 API。

## 组件

```text
AchievementHook.jar ─┐
                     ├─ HTTP + Bearer Token ─> AchievementSystem.jar ─> PostgreSQL
AchievementGui.jar ──┘                              │
ManiacAchievementHook.jar ─> AchievementHook.jar    │
                                                   ├─ Reward Outbox
                                                   └─ LuckPerms suffix ─> TAB
```

- `AchievementSystem.jar`：Velocity 3.3+，Java 21。
- `AchievementGui.jar`：Lobby Paper 1.21.1，Java 21。
- `AchievementHook.jar` Paper 版：Paper 1.20.4+，Java 17。
- `AchievementHook.jar` Fabric 版：Fabric Loader 0.16.10+，Minecraft
  `1.20.1`–`26.2`，Java 17 字节码。
- `ManiacAchievementHook.jar`：逃离疯子 2 的 Paper 1.21.3 实时成就接入。
- HTTP API 只允许 `127.0.0.1`、`localhost` 或 `::1`。
- API Token、PostgreSQL 和配置均为本系统独立所有，不读取任何 `SCHEDULER_*` 配置。

## 构建

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  ./scripts/build.sh
```

构建结果：

```text
dist/
├── velocity/AchievementSystem.jar
├── lobby/AchievementGui.jar
├── hook/
│   ├── paper/AchievementHook.jar
│   └── fabric/AchievementHook.jar
└── integrations/
    └── maniac2/ManiacAchievementHook.jar
```

生成独立 API Token：

```bash
./scripts/generate-token.sh
```

默认占位 Token 不可运行。必须把同一个至少 32 字符的 Token 写入 System、GUI 和所有
Hook 配置，或使用独立环境变量 `ACHIEVEMENT_API_TOKEN`。

## AchievementSystem

Velocity 数据目录：

```text
plugins/achievement-system/
├── config.yml
└── achievements.yml
```

`config.yml`：

```yaml
api:
  host: "127.0.0.1"
  port: 25567
  token: "64-character-random-token"
  maximum-body-bytes: 65536
database:
  url: "jdbc:postgresql://127.0.0.1:5432/achievements"
  user: "achievements"
  password: "database-password"
  pool-size: 8
badges:
  maximum-selected: 3
  luckperms-suffix-priority: 180
rewards:
  poll-seconds: 5
  batch-size: 50
```

可用的独立环境覆盖：

- `ACHIEVEMENT_API_TOKEN`
- `ACHIEVEMENT_DATABASE_URL`
- `ACHIEVEMENT_DATABASE_USER`
- `ACHIEVEMENT_DATABASE_PASSWORD`

数据库 `achievements` 需要预先创建。System 启动时自动创建和升级以下表：

- `achievement_players`
- `achievement_progress`
- `achievement_unlocks`
- `achievement_badge_selection`
- `achievement_events`
- `achievement_reward_delivery`

进度不会下降。`ADD` 增加进度，`SET` 只能设为不小于当前值，`MAX` 取当前值和上报值的
较大者。Hook 自动为未提供 Event ID 的调用生成 UUID；稳定 Event ID 可以保证网络重试时
同一类别内的同一事件只计算一次。事件记录不会按时间过期，不同类别可以安全复用同一个
来源和 Event ID。每次管理员撤销都会推进该玩家对应类别的事件 generation；`MAX` 和
`SET` 属于状态断言，可以在新 generation 中用同一个 Event ID 再次生效一次，随后继续
保持幂等。`ADD` 表示一次独立增量事件，历史 Event ID 跨 generation 仍然去重，新的真实
事件必须使用新的 Event ID，避免撤销后重放旧请求刷进度。

## 成就类别

每个类别包含一个可配置图案和最多 10 个递进等级。玩家选择的是类别，不是具体等级；类别
升级后，展示徽章自动切换到新颜色。

```yaml
categories:
  puzzle_maps:
    enabled: true
    order: 10
    hidden: false
    badge-enabled: true
    display-name: "谜境探索"
    description:
      - "完成独立解谜地图"
    material: "AMETHYST_SHARD"
    symbol: "⌘"
    tiers:
      - name: "初入谜境"
        threshold: 1
        color: "#9E9E9E"
        rewards:
          - type: "permission"
            value: "achievement.puzzle_maps.tier.1"
            description: "解锁谜境探索 I 徽章"
```

`hidden: true` 的类别在首次完成前只返回“隐藏成就”和占位内容，完成后才向 GUI 揭示真实
标题、描述、图案和等级。`badge-enabled: false` 的类别会显示在总览中，但不能被选择为
LuckPerms suffix。

GUI 总览显示每个类别至少完成一级的全服人数，详情页显示每一级的独立获取人数。隐藏成就
在完成前同样公开人数和每级首位获取者，但不会公开真实标题、描述或奖励；首位获取者从
当前仍拥有该等级的玩家中按 `unlocked_at` 升序选择，时间相同时按 UUID 升序稳定决胜。
撤销后会顺延到下一位，全部撤销后由 GUI 显示 `???`，重新获取按新的解锁时间参与排序，
玩家改名会显示当前名称。高等级解锁会保留全部低等级解锁记录，因此 Bingo King 等递进
成就的低等级人数不会因玩家升级而减少。

仓库默认类别：

- `welcome`：首次进入群组服自动完成，标题为
  `Welcome to SHTechCraft Minigames.`，绿色、单阶段、无额外奖励且不能作为 suffix。授予
  发生在玩家首次成功连接后端服务器之后，确保客户端已经可以接收原版风格的全服完成消息。
- `bingo_hard_3_monthly_first`：`Bingo King`，困难三线月榜第一次数累计 1–10 级，图案为
  `❖`，每级使用独立颜色。
- `maniac_2_everything`：隐藏成就 `Who's the Maniac Now?`，图案为 `✣`，同一局曾拥有全部
  16 把钥匙、6 桶汽油、绝缘胶带、断线钳、螺丝刀、急救箱和药片后立即完成。
- `puzzle_maps`：10 级解谜示例，阈值为
  `1/3/5/7/10/15/20/30/50/100`，图案为 `⌘`，目前保持关闭。

## 奖励

每个等级支持多个奖励：

- `message`：向在线玩家发送 MiniMessage 文本；离线时持续等待玩家上线。
- `velocity-command`：以 Velocity 控制台执行命令。
- `permission`：通过 LuckPerms API 添加全局权限节点。
- `group`：通过 LuckPerms API添加全局继承组。

奖励在解锁事务中写入 Outbox，再异步投递。权限和群组天然幂等；任意 Velocity 命令采用
至少一次投递，命令本身应设计为幂等。失败任务会指数退避，普通失败最多尝试 10 次；离线
消息不会因为重试次数耗尽而丢弃。

每次解锁新等级都会自动生成一条全服完成消息，不需要在 YAML 中重复配置 `message`。
消息颜色取当前等级颜色，隐藏成就在完成消息中揭示真实标题。

奖励值支持：

- `{player}`
- `{uuid}`
- `{category}`
- `{tier}`

管理命令：

```text
/achievementsystem status
/achievementsystem reload
/achievementsystem retry-rewards
/achievementsystem add <player> <category> <amount> [eventId]
/achievementsystem set <player> <category> <amount> [eventId]
/achievementsystem max <player> <category> <amount> [eventId]
/achievements admin status
/achievements admin reload
/achievements admin retry-rewards
/achievements admin view <player|uuid> [category]
/achievements admin grant <player|uuid> <category> [tier|max]
/achievements admin revoke <player|uuid> <category> [tier|all]
```

`/achievements` 的所有参数以及独立管理命令均要求 `achievement.admin`，默认玩家不拥有此
权限，且补全不会泄露管理子命令。Velocity 只接管 `/achievements`，其无参数形式转发到
Lobby GUI；`/achievement` 与 `/ach` 由 Lobby GUI 提供。撤销会降低数据库进度，并删除
对应解锁、待投递奖励和 suffix 选择。
之后 `MAX`/`SET` 状态断言可以用原 Event ID 再次获取，`ADD` 则必须由新的真实事件使用新
Event ID；旧增量请求不会因撤销而重新累计。已经执行过的外部命令或权限奖励不会自动反向
撤销。

## AchievementGui

Lobby 命令：

```text
/achievements
/achievement
/ach
```

GUI 包含：

- 玩家进入 Lobby 后，第五个快捷栏槽位固定提供带发光效果的钻石“成就图鉴”，右键打开与
  `/achievements` 相同的总览页面。
- 54 格成就总览和 28 分类分页。
- 玩家头像总进度摘要。
- 分类详情、10 级路线、7 段进度条和奖励说明。
- 隐藏成就显示每级的首位获取者，尚无人获取时显示 `???`，不提前泄露标题、描述或奖励。
- 独立徽章选择页，只展示已经获得的类别。
- 最多选择 3 个徽章，顺序持久化到 PostgreSQL。
- 所有 HTTP 请求异步执行，Inventory 变更只在 Paper 主线程执行。
- 选择提交由 System 校验，确认成功后 GUI 重新拉取完整快照。

快捷栏入口使用 PDC 标识，只保护插件生成的钻石，不会把普通钻石识别为入口。玩家加入、
重生和插件启用时会恢复入口，死亡时不会掉落，点击、拖动、数字键交换、换手和丢弃均会被
阻止。若第五格已有真实物品，会先将它移动到空闲背包格；背包完全满时保留原物品并提示，
不会覆盖或吞掉物品。插件关闭和玩家离开时只移除带 PDC 标识的入口。

## LuckPerms 与 TAB

System 使用一个保留优先级的全局 LuckPerms suffix，把最多三个徽章聚合为一条：

```text
 &#9E9E9E◆&r &#29B6F6✦&r &#8E24AA❖&r
```

这样不会受 LuckPerms `highest` suffix stacking 限制。每个徽章和整段颜色都会重置，避免
颜色污染后续文本。

Lobby TAB 建议：

```yaml
scoreboard-teams:
  enabled: true
```

```yaml
_DEFAULT_:
  customtabname: "%player%&r"
  tabsuffix: "%luckperms-suffix%&r"
  tagsuffix: "%luckperms-suffix%&r"
```

`tabsuffix` 控制 TAB 列表，开启 `scoreboard-teams` 后 `tagsuffix` 同时控制头顶名牌。

## AchievementHook

Paper 配置：

```yaml
enabled: true
api-url: http://127.0.0.1:25567
api-token: 64-character-random-token
source: puzzle_server
timeout-seconds: 5
retry-attempts: 3
```

Paper 管理命令：

```text
/achievementhook add <player> <category> <amount> [eventId]
/achievementhook set <player> <category> <amount> [eventId]
/achievementhook max <player> <category> <amount> [eventId]
```

权限为 `achievementhook.admin`。

Fabric 配置位于 `config/achievement-hook.json`。Paper 和 Fabric 使用同一个公开 Java API：

```java
AchievementHook.service().add(
  playerUuid,
  playerName,
  "puzzle_maps",
  1,
  "map-id:completion-id"
);
```

消费端以 `provided` 或 `compileOnly` 方式依赖
`dev.shtech:achievement-hook-api:0.1.0-SNAPSHOT`，不能把另一份 Hook API 打入游戏插件或模组。
Paper 消费插件需要在 `plugin.yml` 中声明 `depend: [AchievementHook]`；Fabric 消费模组需要在
`fabric.mod.json` 中声明 `achievement_hook` 依赖。

### Bingo Stats

Bingo Stats 在换月时先结算上一个月的困难三线月榜。最短时间相同的队员均视为并列第一，
结果按月份持久化到 Bingo Stats 的 PostgreSQL JSONB 状态。玩家每月第一次进入 Bingo 时，
模组通过 Hook 使用 `MAX` 同步累计月冠次数，失败不会写入检查标记，下次进入会重试。

### Maniac 2

`ManiacAchievementHook.jar` 在每局游戏内累计玩家曾拥有的目标物品。不同
`minecraft:item_model` 使用位图判定；两把地下室钥匙和 6 桶汽油使用持久化物品标识分别
计数。钥匙、工具、燃料或药品被使用后记录仍保留到本局结束。状态只在 `game_state=1` 且
玩家属于实际游戏队伍时累计，并以 Scheduler 实例 ID、局数和玩家 UUID 组成幂等事件 ID。

## HTTP API

所有端点都要求：

```text
Authorization: Bearer <token>
```

端点：

- `GET /api/v1/health`
- `GET /api/v1/players/<uuid>?name=<name>`
- `PUT /api/v1/players/<uuid>/badges`
- `POST /api/v1/progress`

进度上报示例：

```json
{
  "playerUuid": "28d4ee2b-da4e-33dd-876c-995f9c0c4f64",
  "playerName": "TalexCK",
  "categoryId": "puzzle_maps",
  "operation": "ADD",
  "amount": 1,
  "eventId": "puzzle-01:completion:28d4ee2b",
  "source": "puzzle_server"
}
```

## 测试

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home mvn clean test
```

真实 PostgreSQL 集成测试使用临时数据目录，脚本退出时会停止实例并删除临时文件：

```bash
PG_HOME=/path/to/postgresql \
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home \
  ./scripts/test-postgres.sh
```
