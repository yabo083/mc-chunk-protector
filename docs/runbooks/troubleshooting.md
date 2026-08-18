# 排障手册

| 症状 | 检查与处理 |
|---|---|
| `/cpor` 不可见 | 确认执行者权限等级至少为 2，且服务端日志显示 mod 已加载 |
| 命令提示配置无效 | 查看服务端日志中的完整异常；修复 `<world>/serverconfig/mcchunkprotector/regions.json` 后执行 `/cpor reload` |
| 外部改文件未立即生效 | 等待最多 40 server ticks，或执行 `/cpor reload` |
| 外部编辑与 `/cpor` 同时发生 | 不支持并发写入；保存文件后再执行命令。mod 会尽力检测已发生的覆盖冲突并要求重试 |
| 配置错误后旧保护仍生效 | 这是 last-known-good 设计；无效候选不会替换当前快照 |
| `/setblock` 能在 place 区域写入 | 模式 A 拦截玩家实体放置事件，不拦管理员命令 |
| 新放下的栅栏/红石有单向连接 | 初始 `BlockState` 按调用方结果保留；只冻结写入后的变化 |
| 作物、流体或方块实体仍运行 | scheduled/random/fluid/block-entity tick 不在当前冻结契约内 |

标准构建使用 Java 21、Gradle Wrapper 8.14.3 和 NeoForge ModDev。不要用完整客户端 modpack 直接启动 dedicated server；实际世界验证使用最小 NeoForge 服务端。
