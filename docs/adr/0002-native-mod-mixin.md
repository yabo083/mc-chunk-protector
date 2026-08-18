# ADR-0002: 拦截实现从纯 KubeJS 迁移到 NeoForge Java mod + Mixin

## 日期
2026-02-16

## 状态
accepted

## 背景
最初按"利用 KubeJS"实现两种服务端区块保护（防放置 / 冻结更新）。实测发现问题：
- 防放置（`BlockEvent.EntityPlaceEvent`）KubeJS 版可用。
- **冻结更新不可行**：1.21.1 中 `NeighborNotifyEvent` 的 `setCanceled(true)` 返回值被 `Level.updateNeighborsAt` **丢弃**（源码 patch 级确认），仅 `DiodeBlock.updateNeighborsInFront` 一处可 cancel 生效。用户实测"栅栏连接"感知无效果，正是因此。
- 纯 KubeJS 2101 无法挂到底层 `Level.neighborChanged` / `scheduleTick`（KubeJS 已移除底层事件桥）。

## 决策
**改用 Native NeoForge Java mod + Mixin**：
- 模式 A（防放置）：`net.neoforged.neoforge.event.level.BlockEvent$EntityPlaceEvent`，`setCanceled(true)`。
- 模式 B（冻结更新）：Mixin 短路 `ServerLevel/Level#updateNeighborsAt`、`Level#neighborChanged`，并在 `Level#neighborShapeChanged` 以目标区块为准取消形状更新。后者是防止红石、栅栏等通过 `NeighborUpdater.executeShapeUpdate` 直接写回状态的关键。
- 冻结区块接受调用方计算出的初始 `BlockState`；只阻止写入后的邻居通知和形状重算，不为某种方块重置默认状态。
- 配置由不可变快照提供查询；每 40 server ticks 检查文件元数据，查询热路径不读盘、不解析 JSON、不按保护面积分配对象。
- 构建用 **javac 免 Gradle**（classpath 指向 dev-server 已装 Neoforge libraries），`mod/build.ps1`。
- 配置仍读 `kubejs/config/regions.json`，仅为兼容已经部署的服务器目录；配置由 OP 命令 `/cpor` 管理，不依赖 KubeJS。

## 后果
- 正面：Mixin 覆盖通知和形状写回，冻结可感知且跨区块目标检查正确；不依赖 KubeJS 事件桥；仅服务端逻辑，客户端无副作用。
- 负面 / 代价：需维护 Java 工程与 mixin 描述符；修改 `Level` vanilla 方法有一定 ABI 风险（1.21.1 方法名和 descriptors 已实测匹配）。模式 B 不冻结 scheduled/random/fluid/block-entity tick；该范围若扩大必须另行设计和压测。

## 备选方案（关键）
- **纯 KubeJS 组合拦截**（NeighborNotify + FluidPlace + PistonEvent）：能拦流体涌入/活塞/部分通知，但 1.21.1 中 NeighborNotify 主路径 cancel 无效，红石/栅栏连接更新拦不住 → **为何放弃**：核心需求（冻结更新）达不到，效果不可靠。
- **coremod（纯 JS ASM）免编译**：可行理论上，但 JS-ASM 改 `markAndNotifyBlock`/`neighborChanged` 复杂、无 Mixin 的类型安全与注解支持 → 改为标准 Mixin 更稳。若未来想免 Java 可用 coremod 方案作参考，但当前选 Java mod。
