# 本地验证与部署

## 构建

```powershell
pwsh -NoProfile -File mod\build.ps1
pwsh -NoProfile -File tools\run_index_regression.ps1
```

将 `dist\mods\mcchunkprotector-1.0.0.jar` 放入 MC 1.21.1 / NeoForge 21.1.219 服务端的 `mods\`。不需要 KubeJS、Rhino、GUI 或客户端安装。

配置默认位于 `<world>/serverconfig/mcchunkprotector/regions.json`，每个存档独立。文件不存在时可直接用 `/cpor add ...` 创建；也可复制 `config-schema/regions.example.json`。

## OP 命令

```text
/cpor status
/cpor status <chunkX> <chunkZ>
/cpor add <place|freeze> here
/cpor add <place|freeze> rect <x1> <z1> <x2> <z2>
/cpor remove <place|freeze> here
/cpor remove <place|freeze> rect <x1> <z1> <x2> <z2>
/cpor reload
```

`here` 使用执行者当前位置的区块；矩形参数是区块坐标且包含两端。每条命令只操作执行者所在维度。所有命令要求权限等级 2。

## 运行语义

- 命令保存后立即发布新快照；外部文件变更最多约 40 ticks 后加载。
- 模式 B 接受初始状态，只阻止之后的邻居通知和形状重算写入冻结目标。
- 卸载 mod 后不再拦截。已保存到世界中的异常 `BlockState` 仍会保留，直到后续原版更新改变它。
- 实际世界回归需启动 `dev-server` 并启用 RCON，然后运行 `python tools\freeze_regression.py`。
