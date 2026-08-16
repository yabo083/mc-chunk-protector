# AGENTS.md

给 AI agent 与协作者的工作规则。架构文档地图见 `docs/README.md`（不要复制这里的内容过去）。

## 项目定位（一句话）

MMC 服务端「区块保护」工具：KubeJS 内置拦截（防放置 / 防方块更新）+ C# WPF 外置 GUI（Xaero 地图底图 + 手势选区 + 配置同步），最终部署到甲方服务器。

## 技术栈与版本（不可随意升版本）

| 层 | 选型 | 版本约束 |
|---|---|---|
| MC 服务端 | NeoForge | 21.1.219（MC 1.21.1） |
| 脚本运行时 | KubeJS (NeoForge) | 2101.7.2 build.348，rhino 2101.2.7 |
| GUI | C# / WPF | .NET 8（SDK 8.0.424 已装） |
| 地图数据源 | Xaero World Map | 读 `xaero/world-map/<server>/DIM<n>/mw$-<seed>/{x}_{z}.zip` 内 `region.xaero` |

**锁定理由**：已在 PCL2 本地环境实测，换版本会破坏可与甲方复现的环境。升级需先验证并写 ADR。

## 常用命令

```powershell
# GUI 构建/运行（cwd: gui/）
dotnet build -c Release
dotnet run --project gui/McChunkProtector.Gui -c Debug

# 跑 KubeJS 类型检查（依赖 KubeJS probe 输出的 jsconfig，可选）
# 本地验证：见 docs/runbooks/setup.md

# 打包发布 WPF（生成可部署 exe）
dotnet publish gui -c Release -r win-x64 --self-contained /p:PublishSingleFile=true
```

## 关键约定

1. **不改 MC 核心/其他 mods 源码**。拦截一律走 KubeJS 脚本 + `config-schema/regions.json` 热重载；GUI 与游戏之间**只通过该配置文件交换数据**（写文件 → KubeJS 每 N tick 检查 mtime / `/reload`）。
2. **性能红线**：保护判定必须是 **O(1)**（区块坐标 → HashSet 命中判定）。绝不遍历百万方块。地图渲染走 **LOD 惰性加载**（只加载当前视口所需 tile），严禁一次性读入全图。
3. **配置协议**是 GUI 与 KubeJS 的契约：改动 `config-schema/regions.json` 结构时必须同步更新 schema、KubeJS 读取方、WPF 写出方三处，并写 ADR。
4. **文档同步**：改架构先读 `docs/architecture/` 对应文件；不可逆技术决策写 `docs/adr/`；部署/排障写 `docs/runbooks/`。
5. **产物可复现**：`kubejs-scripts/` 与 `config-schema/` 必须能在任一新鲜 NeoForge 服务器上复制即用，不依赖本机 PCL2 特有路径。

## 完成状态

- [x] 仓库骨架 / git init
- [x] KubeJS 拦截方案 API 调研 + 本地 server 实测确认
- [x] Xaero 地图格式调研 + ADR-0001
- [x] KubeJS 脚本 `kubejs-scripts/ChunkProtector.js`（单文件，NativeEvents + JsonIO 热重载）
- [x] 本地 NeoForge server 验证：0 error 加载 + selfCheck 命中断言通过
- [x] WPF GUI：地图视口/探索网格/选区渲染 + 鼠标(平移/缩放/Ctrl框选)+键盘(WASD/±)手势 + 配置读写/热重载
- [x] 文档：01-context / 02-goals / 03-building-blocks / ADR-0001 / runbooks / data/schema
- [ ] 打包发布（`dotnet publish` 生成可部署 exe）+ 部署 zip

（此清单会随进度更新，不做重复粘贴。）

（此清单会随进度更新，不做重复粘贴。）
