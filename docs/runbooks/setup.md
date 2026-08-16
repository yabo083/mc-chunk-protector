# Runbook · 本地验证与部署

## A. 本地开发验证（repro：仅验证 ChunkProtector 脚本）

完整 modpack（130+ mods 含客户端 mod）**不能**直接作为 dedicated server 跑。脚本验证使用**最小 server**：

```powershell
# 1) 下载并安装 NeoForge server（一次性）
cd dev-server
Invoke-WebRequest https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.219/neoforge-21.1.219-installer.jar -OutFile installer.jar
java -jar installer.jar --installServer

# 2) 放入最小 mod 集（仅 KubeJS + rhino，从 PCL2 mods 目录拷）
#    kubejs-neoforge-2101.7.2-build.348.jar
#    rhino-2101.2.7-build.81.jar
Copy-Item ..\..\.minecraft\versions\Mechanomania\mods\kubejs-neoforge-*.jar mods\

# 3) 放 ChunkProtector 脚本（单文件）
Copy-Item ..\kubejs-scripts\ChunkProtector.js kubejs\server_scripts\

# 4) 接受 EULA 并启动
Set-Content eula.txt 'eula=true'
.\runServer.bat --nogui

# 5) 观察 logs/kubejs/server.log 无报错；/reload 后区域生效
```

## B. 部署到甲方 NeoForge 服务器

1. 服务器已装 KubeJS（同版本系）+ rhino。
2. 复制 `kubejs-scripts/` 内容到 `<server>/kubejs/server_scripts/`。
3. 配置文件：GUI 写出的 `regions.json` 放 `<server>/kubejs/config/`（脚本按该路径读取）。
4. 首次启动或 `/reload` 后生效。

## C. 常见故障

- **服务端启动崩**：多数是客户端 mod 被装入 server → 检查 `mods/` 仅含服务端/通用 mod。
- **脚本未生效**：确认 `server_scripts/` 下有脚本、`config/regions.json` 语法合法（GUI 会校验）。
- **刷屏未知事件**：`ForgeEvents` 事件类名改名 → 见 `docs/adr/` 与 `config-schema/` 版本说明。
