# MC Chunk Protector

适用于 Minecraft 1.21.1 / NeoForge 21.1.219 的服务端区块保护 mod。

它提供两种相互独立的保护模式：

- `place-block`：阻止玩家在目标区块内放置方块。
- `freeze-updates`：允许方块以初始状态放置，但阻止之后的邻居通知和形状更新改写目标区块。

管理员通过简短的 `/cpor` 命令管理保护区域。区域以区块矩形并集保存，可以批量添加、批量移除，也可以通过“先添加大矩形，再挖掉小矩形”的方式创建不规则区域。

## 环境要求

| 项目 | 版本或要求 |
|---|---|
| Minecraft | 1.21.1 |
| Mod Loader | NeoForge 21.1.219 |
| Java | 21 |
| 命令权限 | OP 权限等级 2 |

- 专用服务器只需要在服务端安装，普通玩家客户端不需要安装。
- 单人游戏和局域网主机需要在启动游戏的实例中安装，因为本地客户端同时承载集成服务器。
- 不依赖 KubeJS、Rhino、GUI 或 Xaero 地图。

## 安装

1. 关闭服务器或完全退出游戏实例。
2. 将 `mcchunkprotector-1.0.0.jar` 放入服务器或游戏实例的 `mods/` 目录。
3. 启动服务器或游戏。
4. 使用拥有 OP 权限的账号执行：

```mcfunction
/cpor status
```

能够看到当前维度、区块坐标以及 `place`、`freeze` 状态，即表示加载成功。

配置默认保存在：

```text
<世界存档根目录>/serverconfig/mcchunkprotector/regions.json
```

每个世界存档拥有独立配置。文件不存在时，第一次执行 `/cpor add ...` 会自动创建。

## 命令速查

所有命令要求权限等级 2，并且只作用于执行命令时所在的维度。

| 命令 | 作用 |
|---|---|
| `/cpor status` | 查询自己当前所在区块 |
| `/cpor status <chunkX> <chunkZ>` | 查询指定区块 |
| `/cpor add place here` | 禁止在当前区块放置方块 |
| `/cpor remove place here` | 解除当前区块的放置保护 |
| `/cpor add freeze here` | 冻结当前区块的后续邻居和形状更新 |
| `/cpor remove freeze here` | 解除当前区块的更新冻结 |
| `/cpor add <place\|freeze> rect <x1> <z1> <x2> <z2>` | 批量添加闭区间矩形 |
| `/cpor remove <place\|freeze> rect <x1> <z1> <x2> <z2>` | 从已有区域中扣除闭区间矩形 |
| `/cpor reload` | 从磁盘重新加载配置 |

`place` 对应配置模式 `place-block`，`freeze` 对应 `freeze-updates`。

## 区块坐标

命令中的坐标全部是区块坐标，不是方块坐标。

- Java 版可在 `F3` 调试界面查看 `Chunk` 坐标。
- 一个区块是 16 x 16 个方块。
- 方块坐标转换为区块坐标时使用向下取整。例如方块 X 坐标 `-1` 和 `-16` 都属于区块 X 坐标 `-1`，方块 X 坐标 `-17` 属于区块 X 坐标 `-2`。
- 矩形包含起点和终点，两组角坐标可以反向填写，mod 会自动排序。

例如：

```mcfunction
/cpor add place rect 10 20 12 22
```

会保护 X 为 `10..12`、Z 为 `20..22` 的 9 个区块。

## 常用操作

### 保护或解除当前区块

禁止放置：

```mcfunction
/cpor add place here
```

解除禁止放置：

```mcfunction
/cpor remove place here
```

冻结后续方块更新：

```mcfunction
/cpor add freeze here
```

解除冻结：

```mcfunction
/cpor remove freeze here
```

### 批量保护矩形区域

```mcfunction
/cpor add place rect 100 -50 120 -30
/cpor add freeze rect 100 -50 120 -30
```

两种模式互相独立，可以只启用一种，也可以在同一区域同时启用。

### 创建不规则区域

先添加一个 3 x 3 的冻结区域：

```mcfunction
/cpor add freeze rect 10 20 12 22
```

再挖掉中心区块：

```mcfunction
/cpor remove freeze rect 11 21 11 21
```

此时外围 8 个区块仍被冻结，中心区块未冻结。也可以扣除更大的矩形或多次扣除，构造 L 形、环形等区块组。

### 查询结果

```mcfunction
/cpor status 11 21
```

示例输出中的关键字段：

```text
place=false freeze=true
```

- `place=true`：该区块禁止玩家放置方块。
- `freeze=true`：该区块会阻止约定范围内的后续方块更新。
- `矩形 place=... freeze=...`：当前维度两种模式在规范化后的矩形数量，不是被保护区块总数。

## 两种模式的精确语义

### `place-block`

- 阻止玩家触发的普通方块放置。
- 不禁止破坏方块。
- 不负责冻结红石、栅栏或其他方块状态。
- 与 `freeze-updates` 可以叠加使用。

### `freeze-updates`

冻结的是目标区块后续收到的普通邻居通知和形状更新，而不是把整个区块卸载或暂停。

- 新方块仍然可以放置，除非同一区块还启用了 `place-block`。
- 新方块保留放置瞬间由原版或其他 mod 计算出的初始 `BlockState`。
- 已存在的冻结方块不会因之后放置、移除或改变相邻方块而重新计算连接状态。
- 因此依次放置两个相邻栅栏时，第一个栅栏可以保持未连接，第二个栅栏可以保留放置瞬间朝向第一个栅栏的连接。这是预期行为。
- 跨区块更新同样按“被更新目标所在区块”判断；普通区块中的方块继续工作，但不能改写冻结区块中的目标状态。

当前冻结契约不拦截以下行为：

- scheduled tick；
- random tick；
- fluid tick；
- block entity 自身 tick。

这些行为涉及机器、流体、作物和方块实体的运行语义，当前版本不会将它们一并暂停。

卸载 mod 后，保护和更新拦截立即停止，不会向区块写入永久的“冻结标记”。但是，已经保存到世界中的特殊方块状态属于正常世界数据，可能继续保留到原版或其他 mod 再次更新它。

## 配置与热重载

通常建议使用 `/cpor` 修改配置。命令会：

1. 读取并完整校验现有配置；
2. 合并或扣除矩形；
3. 在同一目录写入临时文件；
4. 原子替换 `regions.json`；
5. 立即发布新的运行时快照。

手工编辑配置后，可以执行：

```mcfunction
/cpor reload
```

外部文件变化通常也会在约 40 server ticks 后被检测。不要在 `/cpor` 正在修改配置时让其他程序同时写入文件；如需手工编辑，请先保存文件，再执行 `/cpor reload`。

最小配置如下：

```json
{
  "version": 1,
  "regions": []
}
```

完整字段和约束见：

- [`config-schema/regions.schema.json`](config-schema/regions.schema.json)
- [`config-schema/regions.example.json`](config-schema/regions.example.json)
- [`docs/data/schema.md`](docs/data/schema.md)

配置损坏或超过安全限制时，mod 会拒绝新配置并继续使用最后一次有效快照。修改前仍建议备份 `regions.json` 和世界存档。

## 性能与安全边界

- 方块更新热路径只读取不可变快照，不执行磁盘 IO、JSON 解析或按保护面积展开。
- 配置变更检查每 40 server ticks 执行一次轻量文件元数据检查。
- 单个巨大矩形不会展开成数百万个区块对象。
- 配置文件上限为 16 MiB，总矩形上限为 250,000。
- 空间索引、单桶候选、超大矩形和命令几何运算都有独立预算，超限时拒绝配置或命令，而不是无界消耗主线程内存。
- `/cpor` 在命令根节点要求权限等级 2。
- 写入使用同目录临时文件和原子替换；失败时保留旧文件或最后一次有效内存快照。

## 排查问题

### 找不到 `/cpor`

- 确认 jar 位于实际启动实例或服务器的 `mods/` 目录。
- 确认运行的是 Minecraft 1.21.1、NeoForge 21.1.219 和 Java 21。
- 确认账号拥有权限等级 2。
- 检查启动日志中是否出现 `MC Chunk Protector 1.0.0`。

### 修改后没有生效

- 使用 `/cpor status <chunkX> <chunkZ>` 检查的是区块坐标而非方块坐标。
- 检查执行命令时所在维度；主世界、下界和末地分别管理。
- 手工编辑文件后执行 `/cpor reload`。
- 如果重载失败，查看服务端日志中的完整校验错误；运行时会继续使用旧快照。

### 安全卸载

1. 备份世界和 `regions.json`。
2. 关闭服务器或游戏。
3. 从 `mods/` 移除 jar。
4. 重新启动。

卸载后不会继续阻止放置或邻居更新。

## 构建与验证

项目当前使用本地 NeoForge 服务端 classpath 构建：

```powershell
pwsh -NoProfile -File mod\build.ps1
pwsh -NoProfile -File tools\run_index_regression.ps1
```

构建产物：

```text
dist/mods/mcchunkprotector-1.0.0.jar
```

实际世界回归需要启动 `dev-server` 并启用 RCON：

```text
python tools/cpor_regression.py
python tools/freeze_regression.py
```

## 项目目录

```text
config-schema/  JSON 配置协议与示例
mod/            NeoForge mod 源码和构建脚本
dev-server/     本地验证服务器（运行数据被忽略）
tools/          索引、几何和实际世界回归测试
docs/           架构、ADR、数据字典与 runbook
```
