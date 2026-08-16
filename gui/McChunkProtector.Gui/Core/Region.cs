namespace McChunkProtector.Gui.Core;

/// <summary>
/// 保护区模式。
/// </summary>
public enum RegionMode
{
    /// <summary>模式A：阻止玩家放置方块（类出生点保护）。</summary>
    PlaceBlock,

    /// <summary>模式B：允许放置但阻止一切方块更新（block/neighbor update 抑制）。</summary>
    FreezeUpdates,
}

/// <summary>
/// 一个保护选区。
/// </summary>
public sealed class Region
{
    public required string Id { get; init; }

    public required string Name { get; set; }

    /// <summary>维度资源名，如 minecraft:overworld。</summary>
    public required string Dimension { get; set; }

    /// <summary>模式（A 或 B）。</summary>
    public required RegionMode Mode { get; set; }

    /// <summary>是否生效。</summary>
    public bool Enabled { get; set; } = true;

    /// <summary>
    /// 区块矩形（坐标均为区块坐标，含边界），取并集。
    /// 每项为 [minChunkX, minChunkZ, maxChunkX, maxChunkZ]。
    /// </summary>
    public List<long[]> ChunkFences { get; set; } = new();

    public Region Clone() =>
        new()
        {
            Id = Id,
            Name = Name,
            Dimension = Dimension,
            Mode = Mode,
            Enabled = Enabled,
            ChunkFences = ChunkFences.Select(f => (long[])f.Clone()).ToList(),
        };
}

/// <summary>
/// 顶层配置（与 config-schema/regions.schema.json 一致）。
/// </summary>
public sealed class RegionConfig
{
    public int Version { get; set; } = 1;
    public List<Region> Regions { get; set; } = new();
}
