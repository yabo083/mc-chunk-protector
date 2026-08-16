using System.IO;

namespace McChunkProtector.Gui.Core;

/// <summary>
/// 探索热区查询：扫描 Xaero world-map 的 {x}_{z}.zip 文件存在性，
/// 提供"某个 region 是否已被玩家探索"的集合查询。
/// Xaero region 坐标 = zip 文件名，覆盖 32×32 区块 = 512×512 方块。
/// 供渲染层直接画色块，零字节解析、百万 tile 友好。
/// </summary>
public sealed class ExplorationIndex
{
    /// <summary>每个 Xaero region 覆盖的方块数（32 区块 × 16）。</summary>
    public const int RegionSizeBlocks = 512;
    /// <summary>每个 Xaero region 覆盖的区块数。</summary>
    public const int RegionSizeChunks = 32;

    private readonly HashSet<(long rx, long rz)> _explored = new();
    private readonly string _tileDirPath;
    public bool DirectoryFound { get; private set; }

    public ExplorationIndex(string tileDir)
    {
        _tileDirPath = tileDir;
        DirectoryFound = Directory.Exists(tileDir) && Directory.EnumerateFiles(tileDir, "*.zip").Any();
    }

    public string TileDir => _tileDirPath;

    /// <summary>惰性扫描：列出目录下 {rx}_{rz}.zip 并存入 region 集合。</summary>
    public void Scan()
    {
        if (_explored.Count > 0 || !Directory.Exists(_tileDirPath)) return;
        foreach (var f in Directory.EnumerateFiles(_tileDirPath, "*.zip"))
        {
            var parts = Path.GetFileNameWithoutExtension(f).Split('_');
            if (parts.Length == 2 && long.TryParse(parts[0], out var rx) && long.TryParse(parts[1], out var rz))
            {
                _explored.Add((rx, rz));
            }
        }
    }

    /// <summary>查某 region（region 坐标）是否已被探索。</summary>
    public bool IsExploredRegion(long regionX, long regionZ)
    {
        Scan();
        return _explored.Contains((regionX, regionZ));
    }

    /// <summary>从区块坐标查其所属 region 是否已被探索。</summary>
    public bool IsExploredChunk(long chunkX, long chunkZ) =>
        IsExploredRegion(chunkX >> 5, chunkZ >> 5);

    /// <summary>从方块坐标查其所属 region 是否已被探索。</summary>
    public bool IsExploredBlock(long blockX, long blockZ) =>
        IsExploredRegion(blockX >> 9, blockZ >> 9);

    public long ExploredRegionCount => (_explored.Count);
}
