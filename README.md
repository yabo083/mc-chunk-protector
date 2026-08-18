# MC Chunk Protector

MC 1.21.1 / NeoForge 21.1.219 服务端区块保护 mod。

- `place-block`：阻止玩家在命中区块放置方块。
- `freeze-updates`：保留方块的初始放置状态，阻止之后的邻居通知和形状更新改写冻结区块。
- `/cpor`：仅 OP 可用的查询、矩形批量添加/移除和热重载命令。
- `regions.json`：矩形并集持久化；运行时使用不可变空间桶索引，不按保护面积展开。

快速开始与命令说明见 [`docs/runbooks/setup.md`](docs/runbooks/setup.md)。

## 目录

```text
config-schema/  JSON 配置协议与示例
mod/            NeoForge mod 源码和构建脚本
dev-server/     本地验证服务器（运行数据被忽略）
tools/          索引、几何和实际世界回归测试
docs/           架构、ADR、数据字典与 runbook
```
