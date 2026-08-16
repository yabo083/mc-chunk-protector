# §5 构件视图 L1（Building Blocks）

系统拆分为两大「机内构件」+ 一个「数据契约」。只画 L1 白盒。

```mermaid
flowchart LR
    subgraph MC_SERVER["NeoForge 服务端进程"]
        KJS["KubeJS 脚本<br/>(kubejs-scripts)"]
        CFG["regions.json<br/>(kubejs/config)"]
        KJS -- 定时读取 --> CFG
    end

    subgraph GUI_PROG["WPF GUI 进程"]
        GEO["地图/选区引擎<br/>Xaero tile 解析 + LOD"]
        EDIT["选区编辑<br/>鼠标/键盘手势"]
        IO["配置写出<br/>JSON 序列化"]
        GEO --> EDIT --> IO
    end

    IO -- 写文件 --> CFG
    XAERO[(Xaero World Map<br/>本地缓存 x_y.zip)] --> GEO
    GUI_PROG -->|"RenderSurface"| WPF["WPF RenderTargetBitmap"]
```

## 构件职责

| 构件 | 职责 | 关键接口/依赖 |
|---|---|---|
| **KubeJS 脚本**（`kubejs-scripts/`） | 服务端拦截两组事件：防放置（模式A）、防更新（模式B）；维护区块→模式索引；定时读配置+mtime 热重载 | KubeJS `ForgeEvents` / `BlockEvents` / `ServerEvents` |
| **regions.json**（`config-schema/`） | 保护区域唯一契约；GUI 写、脚本读 | JSON Schema `regions.schema.json` |
| **地图/选区引擎**（WPF） | 解析 Xaero `region.xaero` 图块、多级 LOD 渲染底图、区块网格叠加 | 直接读文件（只读） |
| **选区编辑**（WPF） | 手势交互：拖拽平移/滚轮缩放/Ctrl框选/右键取消，WASD 平移 + `-`/`=` 缩放 | 事件路由到引擎 |
| **配置写出**（WPF） | 把选区序列化为合法 `regions.json` | 读 `regions.schema.json` 校验 |

## 数据流（主次两条）

1. **地图流（只读单向）**：`Xaero tiles` → GUI 引擎逐级加载 → 渲染窗口。**不改写 Xaero 数据**。
2. **配置流（写回）**：GUI 编辑选区 → 校验 → 写 `regions.json` → KubeJS 检测并刷新索引 → 拦截生效。
