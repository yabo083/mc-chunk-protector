# 上下文视图

MC Chunk Protector 是运行在 NeoForge 服务端内的区块保护工具。管理员通过 OP 命令 `/cpor` 按维度管理矩形区块并集。

## 模式

- `place-block`：命中区域内玩家不能放置方块，但破坏和交互不受本模式处理。
- `freeze-updates`：允许放置并保留初始 `BlockState`；之后的邻居通知和形状重算不能改写冻结目标。scheduled/random/fluid/block-entity tick 不在契约内。

## 外部接口

| 实体 | 接口 | 说明 |
|---|---|---|
| NeoForge 21.1.219 | 事件与 Mixin | 宿主，MC 1.21.1 |
| 管理员 | `/cpor`，权限等级 2 | 查询、添加、移除、重载 |
| `<world>/serverconfig/mcchunkprotector/regions.json` | JSON 文件 | mod 自有的存档级持久化 |
| 玩家/世界 | 原版游戏行为 | 保护判定只拦截已定义的事件和更新链 |

## 范围外

- GUI、地图渲染和多服务器集中管理。
- 玩家/角色级例外权限。
- scheduled/random/fluid/block-entity tick 的完全暂停。
- 修改 MC 核心或第三方 mod 源码。
