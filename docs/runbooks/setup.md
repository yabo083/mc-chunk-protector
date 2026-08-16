# Runbook · 本地验证与部署

## A. 本地开发验证（repro：验证 ChunkProtector 脚本）

完整 modpack（130+ mods 含客户端 mod）**不能**直接作为 dedicated server 跑。脚本验证使用**最小 server**。以下命令均在本仓库 `dev-server/` 实测通过。

```powershell
# 1) 下载并安装 NeoForge server（一次性）
cd dev-server
Invoke-WebRequest https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.219/neoforge-21.1.219-installer.jar -OutFile installer.jar
java -jar installer.jar --installServer          # 生成 run.bat / run.sh / libraries

# 2) 放入最小 mod 集（仅 KubeJS + rhino，从 PCL2 mods 目录拷）
Copy-Item ..\..\.minecraft\versions\Mechanomania\mods\kubejs-neoforge-*.jar mods\
Copy-Item ..\..\.minecraft\versions\Mechanomania\mods\rhino-*.jar mods\

# 3) 放保护脚本 + 配置文件
Copy-Item ..\kubejs-scripts\ChunkProtector.js kubejs\server_scripts\
Copy-Item ..\config-schema\regions.example.json kubejs\config\regions.json

# 4) 接受 EULA 并启动（无 GUI）
Set-Content eula.txt 'eula=true'
#   可选：加内存 -Xmx3G 到 user_jvm_args.txt
cmd /c "run.bat nogui > server-run.log 2>&1"

# 5) 验证：logs/kubejs/server.log 应出现
#    "Loaded 1/1 KubeJS server scripts ... with 0 errors"
#    "[ChunkProtector] ChunkProtector.js loaded"
#    以及 ServerEvents.loaded 后区域数。每次改脚本重启一次。
```

## B. 部署到甲方 NeoForge 服务器

1. 服务器已装 KubeJS（2101 同版本系）+ rhino。
2. 复制 `kubejs-scripts/ChunkProtector.js` → `<server>/kubejs/server_scripts/`。
3. GUI 写出的 `regions.json` → `<server>/kubejs/config/`（脚本固定读该路径）。
4. 首次启动或 `/reload` 后生效；GUI 保存选区后无需重启即热随（每 2 秒 tick 检测）。

## C. 常见故障

见 `docs/runbooks/troubleshooting.md`（含 Rhino 语法、NativeEvents、JsonIO、global 只读 map 等真实踩坑）。
