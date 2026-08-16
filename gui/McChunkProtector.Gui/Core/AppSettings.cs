using System.Text.Json;

namespace McChunkProtector.Gui.Core;

/// <summary>
/// 用户设置：记住 regions.json 路径与 Xaero 地图目录，跨会话保持。
/// 持久化到 %AppData%/McChunkProtector/settings.json。
/// </summary>
public sealed class AppSettings
{
    private static readonly JsonSerializerOptions JsonOpts = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    public string? RegionConfigPath { get; set; }
    public string? XaeroWorldMapDir { get; set; }

    private static string SettingsPath =>
        System.IO.Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
            "McChunkProtector", "settings.json");

    public static AppSettings Load()
    {
        try
        {
            var p = SettingsPath;
            if (System.IO.File.Exists(p))
                return System.Text.Json.JsonSerializer.Deserialize<AppSettings>(System.IO.File.ReadAllText(p), JsonOpts) ?? new AppSettings();
        }
        catch (Exception) { /* 忽略坏设置 */ }

        return new AppSettings();
    }

    public void Save()
    {
        try
        {
            var p = SettingsPath;
            var dir = System.IO.Path.GetDirectoryName(p)!;
            System.IO.Directory.CreateDirectory(dir);
            System.IO.File.WriteAllText(p, System.Text.Json.JsonSerializer.Serialize(this, JsonOpts));
        }
        catch (Exception) { /* 忽略写失败 */ }
    }
}
