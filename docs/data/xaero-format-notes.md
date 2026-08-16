# Xaero World Map 数据格式 — 本地实测笔记

> 来源：真实 dump `versions/Mechanomania/xaero/world-map/Multiplayer_niumaclub.top/` 数据。
> 状态：初步，随调研深入持续更新。

## 目录结构（实测）

```
xaero/world-map/<server_name>/
├── server_config.txt            # TP 命令格式等
├── null/                        # 主世界 (dimensionTypeId: minecraft:overworld)
│   └── mw$-540754784/           # <seed> 命名的地图实例
│       ├── -1_-1.zip            # 每个文件 = 一个"地图 tile"
│       ├── -1_-2.zip
│       └── ...（实测 6764 个 tile）
├── DIM1/                        # 末地 (minecraft:the_end)
└── DIM-1/                       # 下界
```

- `dimension_config.txt` 内容：`MWName:mw$-540754784:Map 1`、`caveModeType:1`、`dimensionTypeId:minecraft:overworld`。
- 坐标文件命名 `{x}_{z}.zip` —— x,z **同时有正负**，推测为 tile 坐标（不是区块坐标，需确认 tile 覆盖几区块）。

## region.xaero 内部（实测二进制）

每个 zip 内含**单个 `region.xaero`**（例 `-1_-1.zip` 解压后 4.7MB，压缩 187KB）。

### 头部字节（前 128 字节 hex）

```
ff 00 06 00 08 00 01 72 d0 03 0a 00 00 08 00 04 4e 61 6d 65 00 10 6d 69 6e ...
```

可见大量**易读字符串**：`Name` → `minecraft:gravel`、`minecraft:cold_ocean`、`minecraft:kelp`、`Properties`、`age`、`22`。

### 解读

- 文件头不是简单图片，而是**自描述、分层**格式。
- 明显包含**方块状态调色板（block-state palette）**：`0b 00 04 Name ... 00 10 minecraft:gravel` 这类 `Tag` 结构，与 Minecraft **NBT** 的字符串/复合标签编码高度相似（`00 04 4E 61 6D 65` = 字符串 tag(0x08) len=4 "Name"）。
- 同时包含 **paletteIndex → blockstate** 的映射（如前缀 `ff 00 06 00 08 00 01 72 d0 03` 这类高字节计数）。

结论：`region.xaero` = **Xaero 自有的「区块地图 + 状态调色板」压缩格式**，用类 NBT 结构编码。**直接字节级解析成本高**，优先考虑：
1. 复用社区成熟解析（见 adr/0001-… 调研结论）；
2. 或**降级为本工程的地图简化方案**：只读取 `{x}_{z}.zip` 的存在性 = 该 tile 已被玩家探索 → 渲染"探索热区网格"，叠加选区——零字节解析、天然支持百万 tile、O(文件数) 惰性。
