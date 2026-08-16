using System.IO;
using System.Text.Json;
using McChunkProtector.Gui.Core;

namespace McChunkProtector.Gui.Core;

/// <summary>
/// 读取/写出 regions.json，与 config-schema/regions.schema.json 保持契约一致。
/// </summary>
public sealed class RegionConfigStore
{
    private static readonly JsonSerializerOptions JsonOpts = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
    };

    public string ConfigPath { get; private set; }

    public RegionConfigStore(string configPath)
    {
        ConfigPath = configPath;
    }

    /// <summary>切换配置文件（GUI 用户手动选择时用）。</summary>
    public void ReloadPath(string newPath) => ConfigPath = newPath;

    /// <summary>读取配置；文件不存在时返回空的合法配置。</summary>
    public RegionConfig Load()
    {
        if (!File.Exists(ConfigPath))
        {
            return new RegionConfig();
        }

        var json = File.ReadAllText(ConfigPath);
        try
        {
            var cfg = JsonSerializer.Deserialize<RegionConfig>(json, JsonOpts);
            return cfg ?? new RegionConfig();
        }
        catch (JsonException)
        {
            // 坏配置：返回空而非崩溃（KubeJS 端也做了容错）。
            return new RegionConfig();
        }
    }

    /// <summary>原子写入（先写临时文件再替换，避免 KubeJS 读到半截）。</summary>
    public void Save(RegionConfig config)
    {
        var json = JsonSerializer.Serialize(config, JsonOpts);
        var tmp = ConfigPath + ".tmp";
        File.WriteAllText(tmp, json);
        File.Move(tmp, ConfigPath, overwrite: true);
    }
}
