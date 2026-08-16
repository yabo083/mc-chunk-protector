using System.IO;
using System.Windows;
using System.Windows.Input;
using McChunkProtector.Gui.Core;

namespace McChunkProtector.Gui;

public partial class MainWindow : Window
{
    private readonly RegionConfigStore _store;
    private readonly AppSettings _settings;
    private RegionConfig _config = new();
    private readonly MapViewport _viewport = new();
    private MapRenderer? _renderer;
    private ExplorationIndex? _exploration;

    // 交互状态
    private bool _panning;
    private bool _boxSelecting;
    private Point _lastMouse;
    private (double x, double z)? _boxStart;

    public MainWindow()
    {
        InitializeComponent();

        _settings = AppSettings.Load();
        var configPath = ResolveConfigPath();
        _store = new RegionConfigStore(configPath);
        StatusText.Text = $"配置: {configPath}";

        KeyDown += OnWindowKeyDown;

        Loaded += (_, _) =>
        {
            _renderer = new MapRenderer(MapCanvas, _viewport)
            {
                ShowExploration = ShowExploration.IsChecked == true,
                ShowGrid = ShowGrid.IsChecked == true,
                ShowRegions = ShowRegions.IsChecked == true,
            };
            LoadEverything();
        };
    }

    // ---------------- 键盘手势：WASD 平移 · +/- 缩放 ----------------
    private void OnWindowKeyDown(object sender, KeyEventArgs e)
    {
        if (_renderer is null) return;
        const double panPx = 60.0;
        const double zoomStep = 0.5;
        switch (e.Key)
        {
            case Key.W: _viewport.Pan(0, -panPx); break;
            case Key.S: _viewport.Pan(0, panPx); break;
            case Key.A: _viewport.Pan(-panPx, 0); break;
            case Key.D: _viewport.Pan(panPx, 0); break;
            case Key.OemPlus:
            case Key.Add:
                ZoomCentered(zoomStep);
                break;
            case Key.OemMinus:
            case Key.Subtract:
                ZoomCentered(-zoomStep);
                break;
            default:
                return;
        }

        e.Handled = true;
        ForceRedraw();
    }

    private void ZoomCentered(double step)
    {
        var size = new System.Windows.Size(MapHost.ActualWidth, MapHost.ActualHeight);
        var (wx, wz) = _viewport.PixelToWorld(size.Width / 2, size.Height / 2, size);
        _viewport.ZoomAt(wx, wz, step);
    }

    private string ResolveConfigPath()
    {
        if (!string.IsNullOrEmpty(_settings.RegionConfigPath) && File.Exists(_settings.RegionConfigPath))
            return _settings.RegionConfigPath!;

        var candidates = new[]
        {
            Path.Combine(AppContext.BaseDirectory, "kubejs", "config", "regions.json"),
            Path.Combine(AppContext.BaseDirectory, "config", "regions.json"),
            Path.Combine(Environment.CurrentDirectory, "kubejs", "config", "regions.json"),
        };

        foreach (var c in candidates)
        {
            var dir = Path.GetDirectoryName(c);
            if (dir != null && Directory.Exists(dir)) return c;
        }

        return candidates[0];
    }

    private void LoadEverything()
    {
        _config = _store.Load();

        // 定位 Xaero 数据集目录（world-map 下某个 <server>/<dim>/mw-* 内含 zip）
        var foundDir = ResolveXaeroTileDir();
        _exploration = new ExplorationIndex(foundDir ?? "");
        if (_exploration.DirectoryFound)
        {
            StatusText.Text += $"  · 探索数据: {Path.GetFileName(Path.GetDirectoryName(foundDir!))} [{_exploration.ExploredRegionCount} regions]";
        }

        RefreshRegionList();
        ForceRedraw();
    }

    private string? ResolveXaeroTileDir()
    {
        // 用户设置优先
        if (!string.IsNullOrEmpty(_settings.XaeroWorldMapDir))
        {
            var f = FindFirstTileDir(new[] { _settings.XaeroWorldMapDir! });
            if (f != null) return f;
        }

        // 自动：常见位置探测
        var roots = new List<string>();
        if (!string.IsNullOrEmpty(_settings.XaeroWorldMapDir)) roots.Add(_settings.XaeroWorldMapDir!);

        foreach (var drive in new[] { "C", "D", "E" })
        {
            var p = Path.Combine($@"{drive}:\SteamLibrary\steamapps\common\PCL2\.minecraft", "versions");
            if (Directory.Exists(p))
            {
                foreach (var ver in Directory.EnumerateDirectories(p))
                {
                    var wm = Path.Combine(ver, "xaero", "world-map");
                    if (Directory.Exists(wm)) roots.Add(wm);
                }
            }
        }

        return FindFirstTileDir(roots.ToArray());
    }

    private static string? FindFirstTileDir(string[] roots)
    {
        try
        {
            foreach (var root in roots)
            {
                if (!Directory.Exists(root)) continue;
                foreach (var server in Directory.EnumerateDirectories(root))
                {
                    foreach (var dim in Directory.EnumerateDirectories(server))
                    {
                        foreach (var mw in Directory.EnumerateDirectories(dim, "mw-*"))
                        {
                            if (Directory.GetFiles(mw, "*.zip").Length > 0) return mw;
                        }
                    }
                }
            }
        }
        catch (Exception)
        {
            /* 路径不可访问时忽略 */
        }

        return null;
    }

    private void RefreshRegionList()
    {
        var items = _config.Regions.Select(r =>
        {
            var fence = r.ChunkFences.Count > 0
                ? $"  {r.ChunkFences.Sum(f => Area(f))} 区块"
                : "  (未选择范围)";
            return new RegionListItem
            {
                Region = r,
                Name = r.Name,
                ModeBadge = r.Mode == RegionMode.PlaceBlock ? "A·防放置" : "B·防更新",
                FenceSummary = fence,
                Enabled = r.Enabled,
            };
        }).ToList();
        RegionList.ItemsSource = items;
        DelRegionBtn.IsEnabled = items.Count > 0;
    }

    private static long Area(long[] f) =>
        f.Length >= 4 ? (f[2] - f[0] + 1) * (f[3] - f[1] + 1) : 0;

    private void ForceRedraw()
    {
        if (_renderer is null || _exploration is null) return;
        var vmItems = RegionList.ItemsSource as System.Collections.IList;
        var regions = _config.Regions;
        _renderer.Render(regions, _exploration);
        UpdateCoordText();
    }

    private void UpdateCoordText()
    {
        var size = new System.Windows.Size(MapHost.ActualWidth, MapHost.ActualHeight);
        var (wx, wz) = _viewport.PixelToWorld(size.Width / 2, size.Height / 2, size);
        var cx = (long)Math.Floor(wx / 16.0);
        var cz = (long)Math.Floor(wz / 16.0);
        CoordText.Text = $"世界: {Math.Round(wx):N0}, {Math.Round(wz):N0}   区块: {cx}, {cz}   缩放: {_viewport.Zoom:0.#}";
    }

    // ---------------- 地图交互 ----------------

    private void Map_MouseWheel(object sender, MouseWheelEventArgs e)
    {
        var pos = e.GetPosition(MapHost);
        var size = new System.Windows.Size(MapHost.ActualWidth, MapHost.ActualHeight);
        var (wx, wz) = _viewport.PixelToWorld(pos.X, pos.Y, size);
        _viewport.ZoomAt(wx, wz, e.Delta > 0 ? 0.5 : -0.5);
        ForceRedraw();
        e.Handled = true;
    }

    private void Map_MouseDown(object sender, MouseButtonEventArgs e)
    {
        MapHost.Focus();
        var pos = e.GetPosition(MapHost);
        _lastMouse = pos;
        var size = new System.Windows.Size(MapHost.ActualWidth, MapHost.ActualHeight);
        var (wx, wz) = _viewport.PixelToWorld(pos.X, pos.Y, size);

        if (e.RightButton == MouseButtonState.Pressed)
        {
            CancelDrag();
            e.Handled = true;
        }
        else if (Keyboard.Modifiers.HasFlag(ModifierKeys.Control))
        {
            _boxSelecting = true;
            _boxStart = (wx, wz);
            _renderer!.DragSelection = (wx, wz, wx, wz);
            e.Handled = true;
        }
        else
        {
            _panning = true;
            e.Handled = true;
        }
    }

    private void Map_MouseMove(object sender, MouseEventArgs e)
    {
        var pos = e.GetPosition(MapHost);
        var size = new System.Windows.Size(MapHost.ActualWidth, MapHost.ActualHeight);
        var (wx, wz) = _viewport.PixelToWorld(pos.X, pos.Y, size);
        UpdateCoordText();

        if (_panning)
        {
            var dx = pos.X - _lastMouse.X;
            var dy = pos.Y - _lastMouse.Y;
            _viewport.Pan(dx, dy);
            _lastMouse = pos;
            ForceRedraw();
        }
        else if (_boxSelecting && _boxStart is { } start && _renderer != null)
        {
            _renderer.DragSelection = (start.x, start.z, wx, wz);
            ForceRedraw();
        }
    }

    private void Map_MouseUp(object sender, MouseButtonEventArgs e)
    {
        if (_boxSelecting)
        {
            _boxSelecting = false;
            var size = new System.Windows.Size(MapHost.ActualWidth, MapHost.ActualHeight);
            var pos = e.GetPosition(MapHost);
            var (ex, ez) = _viewport.PixelToWorld(pos.X, pos.Y, size);
            if (_boxStart is { } start)
            {
                CommitBoxSelection(start, ex, ez);
            }

            if (_renderer != null) _renderer.DragSelection = null;
            ForceRedraw();
            e.Handled = true;
        }

        _panning = false;
    }

    private void CommitBoxSelection((double x, double z) start, double endX, double endZ)
    {
        // 框选生成一个区块矩形选区，附加到当前选中或新建的 region
        long minCx = (long)Math.Floor(Math.Min(start.x, endX) / 16.0);
        long maxCx = (long)Math.Floor(Math.Max(start.x, endX) / 16.0);
        long minCz = (long)Math.Floor(Math.Min(start.z, endZ) / 16.0);
        long maxCz = (long)Math.Floor(Math.Max(start.z, endZ) / 16.0);

        // 若有选中的 region，追加矩形；否则新建
        var target = _config.Regions.FirstOrDefault(r => r.Id == SelectedRegionId);
        if (target is null)
        {
            target = new Region
            {
                Id = Guid.NewGuid().ToString(),
                Name = $"新选区 {_config.Regions.Count + 1}",
                Dimension = "minecraft:overworld",
                Mode = CurrentMode,
            };
            _config.Regions.Add(target);
        }

        target.ChunkFences.Add(new long[] { minCx, minCz, maxCx, maxCz });
        RefreshRegionList();
    }

    private string? SelectedRegionId =>
        (RegionList.SelectedItem as RegionListItem)?.Region.Id;

    private RegionMode CurrentMode =>
        (ModeCombo.SelectedItem as System.Windows.Controls.ComboBoxItem)?.Tag as string == "freeze-updates"
            ? RegionMode.FreezeUpdates
            : RegionMode.PlaceBlock;

    private void CancelDrag()
    {
        _boxSelecting = false;
        _panning = false;
        if (_renderer != null) _renderer.DragSelection = null;
        ForceRedraw();
    }

    // ---------------- 按钮 ----------------

    private void AddRegion_Click(object sender, RoutedEventArgs e)
    {
        var region = new Region
        {
            Id = Guid.NewGuid().ToString(),
            Name = $"新选区 {_config.Regions.Count + 1}",
            Dimension = "minecraft:overworld",
            Mode = CurrentMode,
        };
        _config.Regions.Add(region);
        RefreshRegionList();
        RegionList.SelectedIndex = _config.Regions.Count - 1;
    }

    private void DeleteRegion_Click(object sender, RoutedEventArgs e)
    {
        if (SelectedRegionId is not { } id) return;
        _config.Regions.RemoveAll(r => r.Id == id);
        RefreshRegionList();
        ForceRedraw();
    }

    private void Save_Click(object sender, RoutedEventArgs e)
    {
        _store.Save(_config);
        _settings.RegionConfigPath = _store.ConfigPath;
        _settings.Save();
        StatusText.Text = $"已保存 -> {_store.ConfigPath}";
    }

    private void OpenMapDir_Click(object sender, RoutedEventArgs e)
    {
        var fbd = new Microsoft.Win32.OpenFolderDialog
        {
            Title = "选择 Xaero world-map 数据目录（含各 DIM/mw-* 的文件夹）",
        };
        // 兼容旧版 .NET（若没有 OpenFolderDialog 退回文件夹浏览器）
        if (fbd.ShowDialog() != true) return;
        _settings.XaeroWorldMapDir = fbd.FolderName;
        _settings.Save();
        LoadEverything();
    }

    private void OpenConfig_Click(object sender, RoutedEventArgs e)
    {
        var ofd = new Microsoft.Win32.OpenFileDialog
        {
            Title = "选择 regions.json",
            Filter = "JSON 配置文件 (*.json)|*.json",
        };
        if (ofd.ShowDialog() == true)
        {
            _settings.RegionConfigPath = ofd.FileName;
            _settings.Save();
            _store.ReloadPath(ofd.FileName);
            LoadEverything();
            StatusText.Text = $"配置: {ofd.FileName}";
        }
    }

    private void Reload_Click(object sender, RoutedEventArgs e)
    {
        LoadEverything();
    }

    private void LayerToggle_Changed(object sender, RoutedEventArgs e)
    {
        if (_renderer == null) return;
        _renderer.ShowExploration = ShowExploration.IsChecked == true;
        _renderer.ShowGrid = ShowGrid.IsChecked == true;
        _renderer.ShowRegions = ShowRegions.IsChecked == true;
        ForceRedraw();
    }

    private void BaseMap_Changed(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        if (_renderer == null) return;
        ForceRedraw();
    }

    private void RegionList_SelectionChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        var item = RegionList.SelectedItem as RegionListItem;
        ModeCombo.SelectedIndex = item?.Region.Mode == RegionMode.FreezeUpdates ? 1 : 0;
        ForceRedraw();
    }

    // 文本框显示 item
    public sealed class RegionListItem
    {
        public required Region Region { get; init; }
        public required string Name { get; init; }
        public required string ModeBadge { get; init; }
        public required string FenceSummary { get; init; }
        public required bool Enabled { get; init; }
    }
}
