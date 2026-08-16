# 排障手册

> 这些是本项目开发/验证过程中真实踩过的坑，未来维护优先查这里。

## ChunkProtector.js 语法/运行

| 症状 | 根因 | 解决 |
|---|---|---|
| `signature: SyntaxError: invalid object initializer` | KubeJS/Rhino **不支持 ES6 对象属性简写** `{minX,minZ}` | 写成 `{minX:minX, minZ:minZ}` |
| `SyntaxError: missing name after . operator` | Rhino 不支持**可选链** `?.` | 用 `a.b ? ... : ...` 或 if 判断 |
| `ReferenceError: "globalThis" is not defined` | KubeJS 脚本没有 `globalThis` | 用 KubeJS 提供的全局 **`global`** 对象 |
| `ReferenceError: "globalThis"…` / `UnsupportedOperationException (UnmodifiableMap.put)` | 往 `global` 上的 Java 只读 Map 写字段 | **不要**用 `global.ChunkProtector` 存可变数据；改**单文件闭包** `STATE`（本仓库已这样写） |
| `loadClass Path FAIL: Class is not allowed by class filter!` | KubeJS **禁止** `Java.loadClass('java.nio.file.*')` | 用全局 **`JsonIO.read('kubejs/config/xxx.json')`**（实测返回 JS map），**无需 Path** |
| `Utils.getKubeJS()` 不存在 | 2101 里脚本 `Utils` 是 `UtilsWrapper`，无该方法 | 读配置一律用 `JsonIO.read('路径')` |

## 事件注册

- 全局事件桥**不是 `ForgeEvents`**，而是 **`NativeEvents.onEvent('包.类$内部类', fn)`**（实测成功加载事件类）。
- 放 置：`net.neoforged.neoforge.event.level.BlockEvent$EntityPlaceEvent` → 取消 `event.setCanceled(true)`。
- 方块更新：`net.neoforged.neoforge.event.level.BlockEvent$NeighborNotifyEvent` → 取消同上。
- 事件回调里拿 pos：`event.getPos()`；world：`event.getLevel()`；维度：`String(level.dimension().location())`。

## 本地 server 复现要点（Windows）

1. 用 NeoForge installer 生成 dev-server（见 `setup.md`）。
2. `mods/` 只放 `kubejs-neoforge-*.jar` + `rhino-*.jar`（不要整套 modpack，脚本验证足够）。
3. `cmd /c run.bat nogui` 启动；日志 `logs/kubejs/server.log`。
4. 每次改脚本**重启** server 即可（`/reload` 也触发 `ServerEvents.loaded`，但重启最干净）。
