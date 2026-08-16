# MC Chunk Protector — 服务端区块保护工具

一个运行在 Minecraft NeoForge 服务端上的**区块保护工具**：
- **内置（KubeJS）**：按「区块范围」拦截两种操作——**区块锁定 A（防放置）**和**区块冻结 B（防方块更新）**，配置驱动 + `/reload` 热重载。
- **外置（C# WPF GUI）**：读取本地 **Xaero 世界地图数据**做底图，叠加选区图层，用鼠标/键盘手势管理海量区块选区（支持百万区块级地图的 LOD 惰性渲染），并把选区写回 KubeJS 配置。

> 最终部署到甲方 MC 服务器；本仓库在本地 PCL2 环境（MC 1.21.1 / NeoForge 21.1.219 / KubeJS 2101.7.2）开发并验证。

## 快速开始

见 [`docs/runbooks/setup.md`](docs/runbooks/setup.md)（含本地开发与部署到服务器两套流程）与 [`docs/README.md`](docs/README.md)。

## 仓库目录

```
├── kubejs-scripts/      # 部署到服务器的 KubeJS 脚本（server_scripts）
├── config-schema/       # 配置文件协议定义（JSON Schema + 默认值）
├── gui/                 # C# WPF 桌面 GUI
├── dev-server/          # 本地验证用 NeoForge server（数据目录被 gitignore）
└── docs/
    ├── architecture/    # arc42lite 架构文档
    ├── adr/             # 架构决策记录（MADR）
    └── runbooks/        # 部署与排障
```

其余内容请走 [`docs/`](docs/README.md)，这里不再重复。
