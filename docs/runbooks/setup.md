# Runbook · 本地验证与部署

## A. 本地开发验证（repro：验证 mcchunkprotector mod）

完整 modpack（130+ mods 含客户端 mod）**不能**直接作为 dedicated server 跑。mod 验证使用**最小 server**。以下命令均在本仓库 `dev-server/` 实测通过。

```powershell
# 1) 下载并安装 NeoForge server（一次性）
cd dev-server
Invoke-WebRequest https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.219/neoforge-21.1.219-installer.jar -OutFile installer.jar
java -jar installer.jar --installServer          # 生成 run.bat / run.sh / libraries

# 2) 放入最小 mod 集（mcchunkprotector + KubeJS + rhino）
Copy-Item ..\..\.minecraft\versions\Mechanomania\mods\kubejs-neoforge-*.jar mods\
Copy-Item ..\..\.minecraft\versions\Mechanomania\mods\rhino-*.jar mods\
Copy-Item ..\dist\mods\mcchunkprotector-1.0.0.jar mods\

# 3) 放配置文件
Copy-Item ..\config-schema\regions.example.json kubejs\config\regions.json

# 4) 接受 EULA 并启动（无 GUI）
Set-Content eula.txt 'eula=true'
#   可选：加内存 -Xmx3G 到 user_jvm_args.txt
cmd /c "run.bat nogui > server-run.log 2>&1"

# 5) 验证：latest.log 应出现
#    "[ChunkProtector] loaded <n> freeze fences and <n> place fences"
#    "[ChunkProtector] init, config=<server>/kubejs/config/regions.json"
# 修改 Java 源码后重新构建并替换 jar；仅改 regions.json 无需重启。
```

## B. 部署到甲方 NeoForge 服务器

1. 服务器已装 NeoForge 21.1.219、`mcchunkprotector`、KubeJS（2101 同版本系）+ rhino。
2. GUI 写出的 `regions.json` → `<server>/kubejs/config/`（mod 固定读该路径）。
3. 首次启动后生效；GUI 保存选区后无需重启，mod 每 40 server ticks（约 2 秒）检查配置文件元数据。
4. 模式 B 接受放置时的初始状态，只阻止后续邻居通知和形状重算写入冻结区块；不承诺冻结 scheduled/random/fluid/block-entity tick。
5. 卸载 mod 后不会读取或写入冻结标记；但此前已保存的异常 BlockState 仍属于世界数据，直到后续原版更新改变它。

## C. 常见故障

见 `docs/runbooks/troubleshooting.md`（含 Rhino 语法、NativeEvents、JsonIO、global 只读 map 等真实踩坑）。
