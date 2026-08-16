using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;
using McChunkProtector.Gui.Core;

namespace McChunkProtector.Gui;

/// <summary>
/// 在地图 Canvas 上绘制图层：探索区域、区块网格、保护区选区。
/// 使用纯 WPF 矢量绘制（Shape），对百万级地图按"仅绘制当前视口可见项"控制开销。
/// </summary>
public sealed class MapRenderer
{
    private readonly Canvas _canvas;
    private readonly MapViewport _viewport;

    public bool ShowExploration { get; set; } = true;
    public bool ShowGrid { get; set; } = true;
    public bool ShowRegions { get; set; } = true;

    public MapRenderer(Canvas canvas, MapViewport viewport)
    {
        _canvas = canvas;
        _viewport = viewport;
    }

    public Size ViewportSize => new(_canvas.ActualWidth, _canvas.ActualHeight);

    /// <summary>重绘所有图层。</summary>
    public void Render(IReadOnlyList<Region> regions, ExplorationIndex exploration)
    {
        _canvas.Children.Clear();
        if (ViewportSize.Width <= 0 || ViewportSize.Height <= 0) return;

        if (ShowRegions) DrawRegions(regions);
        if (ShowExploration) DrawExploration(exploration);
        if (ShowGrid) DrawGrid();
        DrawSelectionOverlay(regions);
    }

    /// <summary>当前框选矩形（世界坐标），由交互层设置。</summary>
    public (double x1, double z1, double x2, double z2)? DragSelection { get; set; }

    private void DrawExploration(ExplorationIndex exploration)
    {
        // 探索底图按 Xaero region（512×512 方块）粒度绘制：zip 存在==已探索。
        var (minWx, minWz, maxWx, maxWz) = ViewportWorldRect();
        var size = ViewportSize;
        var fill = new SolidColorBrush(Color.FromArgb(42, 255, 200, 80));

        var minRx = (long)Math.Floor(minWx / ExplorationIndex.RegionSizeBlocks);
        var maxRx = (long)Math.Ceiling(maxWx / ExplorationIndex.RegionSizeBlocks);
        var minRz = (long)Math.Floor(minWz / ExplorationIndex.RegionSizeBlocks);
        var maxRz = (long)Math.Ceiling(maxWz / ExplorationIndex.RegionSizeBlocks);

        // 视口内 region 数量封顶（防止高缩放下几何爆炸）
        const long maxRegions = 4096;
        var w = maxRx - minRx + 1;
        var h = maxRz - minRz + 1;
        if (w * h > maxRegions)
        {
            // 极端全图视角：直接跳过逐个 Shape，提示用网格模式
            return;
        }

        var regionPx = ExplorationIndex.RegionSizeBlocks * _viewport.BlocksPerPixel;
        for (long rz = minRz; rz <= maxRz; rz++)
        {
            for (long rx = minRx; rx <= maxRx; rx++)
            {
                if (!exploration.IsExploredRegion(rx, rz)) continue;
                var (px, pz) = _viewport.WorldToPixel(rx * ExplorationIndex.RegionSizeBlocks, rz * ExplorationIndex.RegionSizeBlocks, size);
                var rect = new Rectangle
                {
                    Width = regionPx,
                    Height = regionPx,
                    Fill = fill,
                };
                Canvas.SetLeft(rect, px);
                Canvas.SetTop(rect, pz);
                _canvas.Children.Add(rect);
            }
        }
    }

    private void DrawGrid()
    {
        var (minWx, minWz, maxWx, maxWz) = ViewportWorldRect();
        var size = ViewportSize;

        var minCx = (long)Math.Floor(minWx / 16.0);
        var maxCx = (long)Math.Ceiling(maxWx / 16.0);
        var minCz = (long)Math.Floor(minWz / 16.0);
        var maxCz = (long)Math.Ceiling(maxWz / 16.0);

        const long step = 8; // 网格线每 8 区块一条（可配，避免过密）
        var brush = new SolidColorBrush(Color.FromArgb(90, 255, 255, 255));
        for (long cx = minCx; cx <= maxCx; cx += step)
        {
            var (px, _) = _viewport.WorldToPixel(cx * 16.0, 0, size);
            var line = new Line
            {
                X1 = px, Y1 = 0, X2 = px, Y2 = size.Height,
                Stroke = brush, StrokeDashArray = new DoubleCollection { 2, 3 },
            };
            _canvas.Children.Add(line);
        }

        for (long cz = minCz; cz <= maxCz; cz += step)
        {
            var (_, pz) = _viewport.WorldToPixel(0, cz * 16.0, size);
            var line = new Line
            {
                X1 = 0, Y1 = pz, X2 = size.Width, Y2 = pz,
                Stroke = brush, StrokeDashArray = new DoubleCollection { 2, 3 },
            };
            _canvas.Children.Add(line);
        }
    }

    private void DrawRegions(IReadOnlyList<Region> regions)
    {
        var size = ViewportSize;
        foreach (var reg in regions)
        {
            if (!reg.Enabled) continue;
            var fill = new SolidColorBrush(Color.FromArgb(110, 80, 180, 255));
            if (reg.Mode == RegionMode.FreezeUpdates)
                fill = new SolidColorBrush(Color.FromArgb(110, 255, 120, 80));
            var strokeBrush = new SolidColorBrush(fill.Color);

            foreach (var f in reg.ChunkFences)
            {
                if (f.Length < 4) continue;
                long minCx = f[0], minCz = f[1], maxCx = f[2], maxCz = f[3];
                // 裁剪到视口
                var (px1, pz1) = _viewport.WorldToPixel(minCx * 16.0, minCz * 16.0, size);
                var (px2, pz2) = _viewport.WorldToPixel((maxCx + 1) * 16.0, (maxCz + 1) * 16.0, size);
                var rect = new Rectangle
                {
                    Width = Math.Abs(px2 - px1),
                    Height = Math.Abs(pz2 - pz1),
                    Fill = fill,
                    Stroke = strokeBrush,
                    StrokeThickness = 1.2,
                };
                Canvas.SetLeft(rect, Math.Min(px1, px2));
                Canvas.SetTop(rect, Math.Min(pz1, pz2));
                _canvas.Children.Add(rect);
            }
        }
    }

    /// <summary>绘制正在拖拽的框选矩形（虚线）。</summary>
    private void DrawSelectionOverlay(IReadOnlyList<Region> regions)
    {
        if (DragSelection is not { } s) return;
        var size = ViewportSize;
        var (px1, pz1) = _viewport.WorldToPixel(s.x1, s.z1, size);
        var (px2, pz2) = _viewport.WorldToPixel(s.x2, s.z2, size);
        var rect = new Rectangle
        {
            Width = Math.Abs(px2 - px1),
            Height = Math.Abs(pz2 - pz1),
            Stroke = Brushes.Lime,
            StrokeThickness = 1.5,
            StrokeDashArray = new DoubleCollection { 4, 2 },
            Fill = new SolidColorBrush(Color.FromArgb(40, 0, 255, 0)),
        };
        Canvas.SetLeft(rect, Math.Min(px1, px2));
        Canvas.SetTop(rect, Math.Min(pz1, pz2));
        _canvas.Children.Add(rect);
    }

    private (double minX, double minZ, double maxX, double maxZ) ViewportWorldRect()
    {
        var size = ViewportSize;
        var (tlx, tlz) = _viewport.TopLeft(size);
        return (tlx, tlz, tlx + size.Width * _viewport.BlocksPerPixel, tlz + size.Height * _viewport.BlocksPerPixel);
    }
}
