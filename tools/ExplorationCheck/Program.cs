using System.Diagnostics;
using McChunkProtector.Gui.Core;

// 验证：ExplorationIndex 能扫描真实 Xaero 世界地图数据并 O(1) 命中。
// 用法：dotnet run --project tools/ExplorationCheck -- <XaeroTileDir>  (可选)
// 默认扫描本机 PCL2 的 Mechanomania 主世界。

var candleDirs = new List<string>();
if (args.Length > 0) candleDirs.Add(args[0]);

// 自动探测本机 Mechanomania 主世界 mw$-* 目录
foreach (var drive in new[] { "C", "D", "E" })
{
    var root = $@"{drive}:\SteamLibrary\steamapps\common\PCL2\.minecraft\versions";
    if (!Directory.Exists(root)) continue;
    foreach (var ver in Directory.EnumerateDirectories(root))
    {
        var wm = Path.Combine(ver, "xaero", "world-map");
        if (!Directory.Exists(wm)) continue;
        foreach (var server in Directory.EnumerateDirectories(wm))
        {
            foreach (var dim in Directory.EnumerateDirectories(server))
            {
                foreach (var mw in Directory.EnumerateDirectories(dim, "mw*"))
                {
                    if (Directory.EnumerateFiles(mw, "*.zip").Any())
                        candleDirs.Add(mw);
                }
            }
        }
    }
}

// 优先主世界（null/dim%0），否则第一个
candleDirs = candleDirs.Where(d => d.Contains("null")).Concat(candleDirs.Where(d => !d.Contains("null"))).ToList();

var found = candleDirs.FirstOrDefault();
if (found is null)
{
    Console.WriteLine("[check] 未找到 Xaero 地图目录。可用参数指定: dotnet run -- <路径>");
    return;
}

Console.WriteLine($"[check] 扫描目录: {found}");
var sw = Stopwatch.StartNew();
var index = new ExplorationIndex(found);
index.Scan();
sw.Stop();

Console.WriteLine($"[check] 扫描耗时: {sw.ElapsedMilliseconds} ms");
Console.WriteLine($"[check] 已探索 region 数: {index.ExploredRegionCount}");

// 命中验证（O(1)）：采样真实存在的 region 文件做命中测试
if (index.ExploredRegionCount == 0)
{
    Console.WriteLine("[check] 无 region，无法命中验证");
    return;
}

var sw2 = Stopwatch.StartNew();
long hits = 0;
var sampled = Directory.EnumerateFiles(index.TileDir, "*.zip").Where((_, i) => i % 97 == 0).ToList();
foreach (var f in sampled)
{
    var parts = Path.GetFileNameWithoutExtension(f).Split('_');
    if (parts.Length != 2) continue;
    if (long.TryParse(parts[0], out var rx) && long.TryParse(parts[1], out var rz))
    {
        if (index.IsExploredRegion(rx, rz)) hits++;
        if (index.IsExploredChunk(rx * 32, rz * 32)) hits++;
    }
}
sw2.Stop();

Console.WriteLine($"[check] {sampled.Count} region ×2 命中验证: {hits} 次命中，耗时 {sw2.ElapsedMilliseconds} ms");
Console.WriteLine(hits > 0
    ? "[check] PASS —— 探索网格可加载真实环境数据，查询 O(1)。"
    : "[check] WARN —— 命中数为 0，检查坐标模型。");
