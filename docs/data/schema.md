# regions.json 数据字典

路径：`<world>/serverconfig/mcchunkprotector/regions.json`。每个世界存档使用独立配置。权威 Schema 为 `config-schema/regions.schema.json`。

| 字段 | 类型 | 说明 |
|---|---|---|
| `version` | int | 当前必须为 `1` |
| `regions[]` | array | 区域记录 |
| `id` | string | 稳定标识；命令生成 `cpor:<dimension>:<mode>` |
| `name` | string | 人类可读名称 |
| `dimension` | resource location | 例如 `minecraft:overworld` |
| `mode` | `place-block` \| `freeze-updates` | 防放置或冻结邻居/形状更新 |
| `enabled` | bool | `false` 时保留但不生效，也不被命令合并 |
| `chunkFences` | `[[minX,minZ,maxX,maxZ], ...]` | 闭区间区块矩形的并集 |

方块坐标到区块坐标使用 floor 除 16：`chunk = block >> 4`，负坐标同样适用。`/cpor` 会把同一维度、同一模式的启用记录合并为一个命令管理记录；禁用记录与其他维度/模式保持不变。

运行时额外限制配置为 16 MiB、总矩形数 250,000，并限制空间桶总引用、单桶重叠候选和超大矩形数量。Schema 表达坐标和单数组上限；跨记录总量与索引形状由 mod 完整校验。
