package com.mcchunkprotector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 冻结区域管理器：读取 regions.json，维护 维度 -> 区块矩形 索引，
 * 提供 O(矩形数) 的命中判定。
 *
 * 配置文件与 KubeJS/GUI 共享同一契约（见 config-schema/regions.schema.json）：
 *   <server>/kubejs/config/regions.json
 */
public final class FrozenRegionManager {
    public static final String PLACE_BLOCK = "place-block";
    public static final String FREEZE_UPDATES = "freeze-updates";
    private static final String DEFAULT_DIM = "minecraft:overworld";

    public record Fence(long minX, long minZ, long maxX, long maxZ) {}

    /** dim -> 防放置矩形列表 */
    private final Map<String, List<Fence>> placeIndex = new HashMap<>();
    /** dim -> 冻结矩形列表 */
    private final Map<String, List<Fence>> freezeIndex = new HashMap<>();

    private final Path configPath;
    private boolean dirty = true;

    private static FrozenRegionManager INSTANCE;

    private FrozenRegionManager(Path configPath) {
        this.configPath = configPath;
    }

    public static synchronized FrozenRegionManager get() {
        return INSTANCE;
    }

    public static synchronized void init(Path configPath) {
        INSTANCE = new FrozenRegionManager(configPath);
    }

    /** 方块坐标 -> 区块坐标 */
    public static long chunkOf(int blockCoord) {
        return blockCoord >> 4;
    }

    /** 解析并刷新索引（仅当文件存在且内容变化时重建）。 */
    public void refresh() {
        try {
            if (!Files.exists(configPath)) {
                clearInternal();
                dirty = false;
                return;
            }
            String raw = Files.readString(configPath);
            Integer sig = raw.hashCode();
            if (!dirty && lastSignature != null && lastSignature == sig) {
                return; // 未变化，跳过
            }
            parse(raw);
            lastSignature = sig;
            dirty = false;
        } catch (Exception e) {
            ChunkProtectorMod.LOG.warn("[ChunkProtector] refresh error: {}", e.toString());
        }
    }

    private Integer lastSignature;

    private void parse(String raw) {
        JsonObject root = new Gson().fromJson(raw, JsonObject.class);
        placeIndex.clear();
        freezeIndex.clear();
        if (root == null || !root.has("regions")) return;

        var arr = root.getAsJsonArray("regions");
        for (var el : arr) {
            var reg = el.getAsJsonObject();
            if (!reg.has("enabled") || !reg.get("enabled").getAsBoolean()) continue;
            String dim = reg.has("dimension") ? reg.get("dimension").getAsString() : DEFAULT_DIM;
            String mode = reg.has("mode") ? reg.get("mode").getAsString() : FREEZE_UPDATES;
            Map<String, List<Fence>> target = mode.equals(PLACE_BLOCK) ? placeIndex : freezeIndex;
            target.computeIfAbsent(dim, k -> new ArrayList<>());
            if (!reg.has("chunkFences")) continue;
            for (var fe : reg.getAsJsonArray("chunkFences")) {
                if (!fe.isJsonArray() || fe.getAsJsonArray().size() < 4) continue;
                var a = fe.getAsJsonArray();
                var f = new Fence(a.get(0).getAsLong(), a.get(1).getAsLong(),
                        a.get(2).getAsLong(), a.get(3).getAsLong());
                target.get(dim).add(f);
                // freeze 区同时也作为 place 的补充索引（语义：冻结=不可变更）
                if (mode.equals(FREEZE_UPDATES)) {
                    placeIndex.computeIfAbsent(dim, k -> new ArrayList<>()).add(f);
                }
            }
        }
    }

    private void clearInternal() {
        placeIndex.clear();
        freezeIndex.clear();
    }

    private static boolean inFence(List<Fence> list, long cx, long cz) {
        if (list == null) return false;
        for (Fence f : list) {
            if (cx >= f.minX() && cx <= f.maxX() && cz >= f.minZ() && cz <= f.maxZ()) return true;
        }
        return false;
    }

    /** 查询某区块是否在"冻结"区（模式 B）。 */
    public boolean isFrozen(String dim, long chunkX, long chunkZ) {
        refresh();
        return inFence(freezeIndex.get(dim), chunkX, chunkZ);
    }

    /** 查询是否在"防放置"区（模式 A，或冻结区）。 */
    public boolean isPlaceBlocked(String dim, long chunkX, long chunkZ) {
        refresh();
        return inFence(placeIndex.get(dim), chunkX, chunkZ);
    }

    public int frozenFenceCount(String dim) {
        refresh();
        List<Fence> l = freezeIndex.get(dim);
        return l == null ? 0 : l.size();
    }
}
