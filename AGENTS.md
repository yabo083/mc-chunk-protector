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

1. **拦截方案 = NeoForge Java mod + Mixin**（已放弃纯 KubeJS）。权威结论：**1.21.1 里 `NeighborNotifyEvent` 的 cancel 返回值被 `Level.updateNeighborsAt` 丢弃，完全无效**（仅 `DiodeBlock.updateNeighborsInFront` 一处能 cancel 生效）。真正"区块冻结"必须 Mixin 短路 `ServerLevel.updateNeighborsAt` / `Level.neighborChanged`。本 mod 用 **Mixin 拦 `ServerLevel.updateNeighborsAt`**，在冻结区块内短路"向邻居广播更新"，实测命中。
2. **不改 MC 核心/其他 mods 源码**。拦截走 mod 的 Mixin + `BlockEvent.EntityPlaceEvent`（防放置）；GUI 与 mod **只通过 `kubejs/config/regions.json` 交换**。
3. **性能红线**：保护判定 **O(1)**（区块坐标 → 矩形命中，mod 用 `pos>>4`）。地图渲染 LOD 惰性加载。
4. **配置协议**是 GUI 与 mod 的契约：改 `config-schema/regions.json` 结构时必须同步 schema、mod 读取方、WPF 写出方三处，并写 ADR。
5. **文档同步**：改架构先读 `docs/architecture/`；不可逆决策写 `docs/adr/`；部署/排障写 `docs/runbooks/`。
6. **产物可复现**：`mod/`（Java 源码 + build.ps1）+ `config-schema/` 复制到任一新鲜 NeoForge 服务器即用。

## 构建 mod（免 Gradle，javac）

```powershell
# 依赖：dev-server 装好 Neoforge 后生成 classpath
#   (Get-ChildItem <dev-server>\libraries -Recurse -Filter *.jar | ? {$_.Name -notmatch 'sources|javadoc'} | % FullName) -join ';' > <dev-server>\classpath.txt

pwsh -NoProfile -File mod\build.ps1       # 输出 dist\mods\mcchunkprotector-1.0.0.jar
```
本地验证：jar 放 dev-server\mods，启动，用 `tools\rcon_client.py` 对着冻结区块发 setblock，看 `server-run.log` 的 `[freeze] short-circuited` 日志。

## 完成状态

- [x] 仓库骨架 / git init
- [x] 三方调研（CancelBlockUpdate 源码级 / NeoForge事件+更新链 / coremod）→ 结论：1.21.1 必须 Mixin，KubeJS 不可行
- [x] Xaero 地图格式调研 + ADR-0001
- [x] **NeoForge mod `mcchunkprotector`**（`mod/`）：模式A=`BlockEvent.EntityPlaceEvent`；模式B=`Mixin 短路 ServerLevel.updateNeighborsAt`
- [x] dev-server 实测：mod 加载成功 + freeze Mixin 命中 `chunk(2,0)` 日志
- [x] WPF GUI：数据层已验证（真实 6764 region 22ms、命中 140/140、O(1)）；UI 编译+启动通过，待人工目检版面
- [x] 已部署 mod 到用户 PCL2（单机/局域网均生效，已移除旧 KubeJS 脚本避免双拦）
- [x] 文档：01-context / 02-goals / 03-building-blocks / ADR-0001 / runbooks / data/schema

（此清单会随进度更新，不做重复粘贴。）
