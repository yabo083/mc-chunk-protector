using System.Windows;

namespace McChunkProtector.Gui.Core;

/// <summary>
/// 地图视口状态：中心世界坐标 + 缩放级别（zoom）。
/// 负责把"世界坐标 <-> 屏幕像素"双向换算，与渲染源解耦。
/// </summary>
public sealed class MapViewport
{
    private const double MinZoom = -4; // 看全图级
    private const double MaxZoom = 8;  // 单区块内
    private const double DefaultZoom = 2.0;
    private const double PixelsPerBlock = 1.0; // zoom=0 时 1 方块 = 1px

    public double CenterX { get; private set; } = 0;
    public double CenterZ { get; private set; } = 0;
    public double Zoom { get; private set; } = DefaultZoom;

    public double BlocksPerPixel => 1.0 / (PixelsPerBlock * Math.Pow(2, Zoom));

    /// <summary>缩放 N 级（+放大/-缩小），光标保持在 anchorX/anchorZ 世界坐标不动。</summary>
    public void ZoomAt(double anchorWorldX, double anchorWorldZ, double deltaZoom)
    {
        var old = BlocksPerPixel;
        Zoom = Math.Clamp(Zoom + deltaZoom, MinZoom, MaxZoom);
        var scale = old / BlocksPerPixel;
        // 保持放大锚点下的世界坐标不变：世界 = center + (px)*bpp
        // old*px = world - centerOld ; new*px = world - centerNew
        double px = (anchorWorldX - CenterX) / old;
        double pz = (anchorWorldZ - CenterZ) / old;
        CenterX = anchorWorldX - px * BlocksPerPixel;
        CenterZ = anchorWorldZ - pz * BlocksPerPixel;
    }

    /// <summary>平移视口（屏幕像素增量 -> 世界坐标位移）。</summary>
    public void Pan(double dxPixels, double dzPixels)
    {
        CenterX += dxPixels * BlocksPerPixel;
        CenterZ += dzPixels * BlocksPerPixel;
    }

    /// <summary>视口左上角世界坐标。size 为视口像素尺寸。</summary>
    public (double x, double z) TopLeft(Size size) =>
        (CenterX - size.Width / 2.0 * BlocksPerPixel, CenterZ - size.Height / 2.0 * BlocksPerPixel);

    public (double x, double z) PixelToWorld(double px, double pz, Size size)
    {
        var (tlx, tlz) = TopLeft(size);
        return (tlx + px * BlocksPerPixel, tlz + pz * BlocksPerPixel);
    }

    public (double px, double pz) WorldToPixel(double wx, double wz, Size size)
    {
        var (tlx, tlz) = TopLeft(size);
        return ((wx - tlx) / BlocksPerPixel, (wz - tlz) / BlocksPerPixel);
    }
}
