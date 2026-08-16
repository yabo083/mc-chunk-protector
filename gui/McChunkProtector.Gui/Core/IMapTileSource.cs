namespace McChunkProtector.Gui.Core;

/// <summary>
/// 一个地图图块：WPF 渲染用 WriteableBitmap（BGRA32）+ 世界坐标范围。
/// </summary>
public sealed class MapTile
{
    public required int MinBlockX { get; init; }
    public required int MinBlockZ { get; init; }
    public required int SizeBlocks { get; init; }
    public required byte[] Argb32 { get; init; }
    public required int PixelWidth { get; init; }
    public required int PixelHeight { get; init; }
}

/// <summary>
/// 地图来源抽象：GUI 从它惰性拉取"某视口世界矩形范围"的底图数据。
/// 具体实现（XaeroZipTileSource / ExplorerGridTileSource）在 adr/0001 决策后选定。
/// </summary>
public interface IMapTileSource
{
    /// <summary>来源显示名（用于 UI 显示当前底图模式）。</summary>
    string DisplayName { get; }

    /// <summary>该来源支持的最大放大级别下，1 方块像素数建议值。</summary>
    double BasePixelsPerBlock { get; }

    /// <summary>获取覆盖 [minX,minZ,maxX,maxZ]（方块坐标）的瓦片集合。</summary>
    IReadOnlyList<MapTile> GetTiles(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ);
}
