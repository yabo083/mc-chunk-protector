# 数据字典（schema）

## regions.json（GUI 写出 / KubeJS 读取）

路径：`<server>/kubejs/config/regions.json`。
权威 JSON Schema：`config-schema/regions.schema.json`。

| 字段 | 类型 | 说明 |
|---|---|---|
| `version` | int | 协议版本，当前 `1`。 |
| `regions[]` | array | 保护区域列表。 |
| `regions[].id` | string | 稳定唯一 id（GUI 生成 UUID）。 |
| `regions[].name` | string | 显示名。 |
| `regions[].dimension` | string | 维度资源名，`minecraft:overworld` / `the_nether` / `the_end`。 |
| `regions[].mode` | `place-block` \| `freeze-updates` | **A 防放置** / **B 防更新**。 |
| `regions[].enabled` | bool | `false` 时保留但暂停。 |
| `regions[].chunkFences` | `[[minX,minZ,maxX,maxZ],…]` | 区块矩形（闭区间），矩形取并集。坐标为**区块坐标**。 |

### 例子
```json
{
  "version": 1,
  "regions": [
    { "id": "uuid-1", "name": "基地", "dimension": "minecraft:overworld",
      "mode": "place-block", "enabled": true,
      "chunkFences": [[10,10,12,12]] }
  ]
}
```

## 坐标系统

- **区块坐标** `(cx,cz)`：1 区块 = 16×16 方块。世界方块 `(bx,bz)` → 区块 `(⌊bx/16⌋, ⌊bz/16⌋)`。
- **Xaero region 坐标** `(rx,rz)`：一个 region = 32×32 区块 = 512×512 方块。
  - region 文件 `{rx}_{rz}.zip` → 覆盖方块 `[rx*512, rz*512]` 起 512×512。
  - 方块 → region：`rx = bx >> 9`。
- regions.json 的 `chunkFences` 用**区块坐标**（避免 GUI/GUI schema 与 Xaero region 混用歧义）。

## schema 变更记录

- **v1**（2026-02）：初始。`regions[].mode` 二元、`chunkFences` 区块矩形列表。
