# AGENTS.md

给 AI agent 与协作者的项目规则。架构地图见 `docs/README.md`。

## 定位与版本

MC 1.21.1 / NeoForge 21.1.219 服务端区块保护 mod。版本已在本地 PCL2 与专用服务器验证，升级前必须重新验证并记录 ADR。

## 关键约定

1. 实现使用 NeoForge Java mod + Mixin，不依赖 KubeJS、GUI 或客户端安装。
2. 模式 A 通过 `BlockEvent.EntityPlaceEvent` 防放置；模式 B 拦截 `ServerLevel/Level.updateNeighborsAt`、`Level.neighborChanged` 和目标侧 `Level.neighborShapeChanged`。
3. 模式 B 接受初始 `BlockState`；scheduled/random/fluid/block-entity tick 不在当前契约内。
4. OP 管理接口只有 `/cpor`。持久化路径保持 `<server>/kubejs/config/regions.json`，仅为兼容现有服务器目录。
5. 性能红线：热查询无磁盘 IO、JSON 解析、装箱和按保护面积展开；配置每 40 server ticks 只检查一次元数据。
6. 修改配置结构时同步 `config-schema/regions.schema.json`、mod 读写实现、回归测试和 ADR。
7. 不修改 MC 或第三方 mod 源码；不提交 `dev-server` 运行数据与本机构建产物。

## 构建与验证

```powershell
pwsh -NoProfile -File mod\build.ps1
pwsh -NoProfile -File tools\run_index_regression.ps1
python tools\freeze_regression.py
pwsh -NoProfile -File scripts\package.ps1
```

`mod\build.ps1` 使用 `dev-server\classpath.txt`，输出 `dist\mods\mcchunkprotector-1.0.0.jar`。
