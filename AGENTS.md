# AGENTS.md

给 AI agent 与协作者的项目规则。架构地图见 `docs/README.md`。

## 定位与版本

MC 1.21.1 / NeoForge 21.1.219 服务端区块保护 mod。版本已在本地 PCL2 与专用服务器验证，升级前必须重新验证并记录 ADR。

## 关键约定

1. 实现使用 NeoForge Java mod + Mixin，不依赖 KubeJS、GUI 或客户端安装。
2. 模式 A 通过 `BlockEvent.EntityPlaceEvent` 防放置；模式 B 拦截 `ServerLevel/Level.updateNeighborsAt`、`Level.neighborChanged` 和目标侧 `Level.neighborShapeChanged`。
3. 模式 B 接受初始 `BlockState`；scheduled/random/fluid/block-entity tick 不在当前契约内。
4. OP 管理接口只有 `/cpor`。持久化路径为 `<world>/serverconfig/mcchunkprotector/regions.json`，每个存档独立。
5. 性能红线：热查询无磁盘 IO、JSON 解析、装箱和按保护面积展开；配置每 40 server ticks 只检查一次元数据。
6. 修改配置结构时同步 `config-schema/regions.schema.json`、mod 读写实现、回归测试和 ADR。
7. 不修改 MC 或第三方 mod 源码；不提交 `dev-server` 运行数据与本机构建产物。

## 构建与验证

```text
./gradlew build
python tools\freeze_regression.py
```

标准构建使用 Java 21、Gradle Wrapper 8.14.3 与 NeoForge ModDev，输出 `build\libs\mcchunkprotector-1.0.0.jar`；`./gradlew build` 已包含两组 standalone 回归。实际世界冻结回归仍需已启动且启用 RCON 的 `dev-server`。
