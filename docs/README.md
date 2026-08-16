# 文档索引

MC Chunk Protector 架构文档（arc42lite 精简版）。源码：纯 Markdown，无重型工具链。

## arc42 § 映射表

| arc42 章节 | 文件 | 状态 |
|---|---|---|
| §1 目标 | `architecture/02-goals-constraints.md` | 草案（待脚本/GUI 落地后回填） |
| §2 约束 | `architecture/02-goals-constraints.md` | 草案 |
| §3 上下文/范围 | `architecture/01-context.md` | ✅ 已写 |
| §4 方案策略 | 并入 `01-context.md` 与 `03-building-blocks.md` | ✅ |
| §5 构件视图 L1 | `architecture/03-building-blocks.md` | 草案待回填 |
| §6 运行时视图 | `architecture/04-runtime-view.md` | 待回填（验证后） |
| §7 部署 | `runbooks/setup.md`（单机+甲方服务器两套） | 待写 |
| §8 横切概念 | 并入 `02-goals-constraints.md`（性能/配置协议） | 草案 |
| §9 架构决策 | `adr/`（MADR） | 待加 0001 |
| §10 质量 | `02-goals-constraints.md` | 草案 |
| §11 风险/技术债 | `runbooks/troubleshooting.md` | 待回填 |
| §12 术语 | `adr/` 内定义 + `data/schema.md` | 待写 |

## 有意省略（Dare to leave gaps）

- **单机 + 单一甲方服务器**即可，删 `05-deployment.md`，部署要点并入 `runbooks/setup.md`。
- **不引入 Diátaxis / docToolchain**：规模增长后再考虑。
- **runbooks 只回填真实踩坑**，不预先堆模板。

## 文档状态

撰写中。核心来源：KubeJS 拦截方案调研 + Xaero 地图格式调研（见 `adr/` 与 `architecture/01-context.md`）。
