package com.mcchunkprotector;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the region file outside the block-update hot path.
 *
 * The runtime interface is deliberately small: a dimension and a chunk
 * coordinate in, a boolean out. The implementation uses immutable snapshots
 * and spatial buckets, so the protected area is never expanded into one
 * object per chunk.
 */
public final class FrozenRegionManager {
    public static final String PLACE_BLOCK = "place-block";
    public static final String FREEZE_UPDATES = "freeze-updates";

    private static final int REFRESH_INTERVAL_TICKS = 40;
    private static final int MAX_CONFIG_BYTES = 16 * 1024 * 1024;
    private static final int MAX_FENCES = 250_000;
    private static final int MIN_CHUNK = Integer.MIN_VALUE >> 4;
    private static final int MAX_CHUNK = Integer.MAX_VALUE >> 4;

    private final Path configPath;
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private int ticksUntilRefresh;
    private long lastSize = -1L;
    private FileTime lastModified;
    private long failedSize = -1L;
    private FileTime failedModified;

    private static volatile FrozenRegionManager INSTANCE;

    private FrozenRegionManager(Path configPath) {
        this.configPath = configPath;
    }

    public static FrozenRegionManager get() {
        return INSTANCE;
    }

    public static void init(Path configPath) {
        var manager = new FrozenRegionManager(configPath);
        INSTANCE = manager;
        manager.refresh();
    }

    /** Called once per server tick; it never iterates loaded or configured chunks. */
    public void tick() {
        if (--ticksUntilRefresh > 0) return;
        ticksUntilRefresh = REFRESH_INTERVAL_TICKS;
        refresh();
    }

    /** Block coordinate to chunk coordinate, matching Minecraft's negative floor division. */
    public static int chunkOf(int blockCoord) {
        return blockCoord >> 4;
    }

    /** Rebuilds the immutable snapshot only when the config file metadata changes. */
    public void refresh() {
        try {
            if (!Files.exists(configPath)) {
                if (lastSize != -1L || snapshot != Snapshot.EMPTY) {
                    snapshot = Snapshot.EMPTY;
                }
                lastSize = -1L;
                lastModified = null;
                failedSize = -1L;
                failedModified = null;
                return;
            }

            BasicFileAttributes attrs = Files.readAttributes(configPath, BasicFileAttributes.class);
            long size = attrs.size();
            FileTime modified = attrs.lastModifiedTime();
            if (size > MAX_CONFIG_BYTES) {
                warnOnce(size, modified, "config exceeds " + MAX_CONFIG_BYTES + " bytes");
                return;
            }
            if (size == lastSize && modified.equals(lastModified)) return;
            if (size == failedSize && modified.equals(failedModified)) return;

            String raw = Files.readString(configPath, StandardCharsets.UTF_8);
            Snapshot parsed = parse(raw);
            snapshot = parsed;
            lastSize = size;
            lastModified = modified;
            failedSize = -1L;
            failedModified = null;
            ChunkProtectorMod.LOG.info("[ChunkProtector] loaded {} freeze fences and {} place fences",
                    parsed.freezeFenceCount(), parsed.placeFenceCount());
        } catch (Exception e) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(configPath, BasicFileAttributes.class);
                warnOnce(attrs.size(), attrs.lastModifiedTime(), e.getMessage());
            } catch (IOException ignored) {
                ChunkProtectorMod.LOG.warn("[ChunkProtector] unable to read config: {}", e.toString());
            }
        }
    }

    private void warnOnce(long size, FileTime modified, String reason) {
        if (size == failedSize && modified.equals(failedModified)) return;
        failedSize = size;
        failedModified = modified;
        ChunkProtectorMod.LOG.warn("[ChunkProtector] keeping last valid config: {}", reason);
    }

    private static Snapshot parse(String raw) {
        JsonObject root = new Gson().fromJson(raw, JsonObject.class);
        if (root == null || !root.has("version") || root.get("version").getAsInt() != 1) {
            throw new IllegalArgumentException("regions.json must have version 1");
        }
        if (!root.has("regions") || !root.get("regions").isJsonArray()) {
            throw new IllegalArgumentException("regions.json regions must be an array");
        }

        Map<ResourceLocation, List<Fence>> place = new HashMap<>();
        Map<ResourceLocation, List<Fence>> freeze = new HashMap<>();
        int fenceCount = 0;
        for (JsonElement element : root.getAsJsonArray("regions")) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("region must be an object");
            JsonObject region = element.getAsJsonObject();
            if (!region.has("enabled") || !region.get("enabled").isJsonPrimitive()
                    || !region.get("enabled").getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("region enabled must be boolean");
            }
            if (!region.get("enabled").getAsBoolean()) continue;

            if (!region.has("dimension") || !region.get("dimension").isJsonPrimitive()) {
                throw new IllegalArgumentException("enabled region has no dimension");
            }
            ResourceLocation dimension = ResourceLocation.tryParse(region.get("dimension").getAsString());
            if (dimension == null) throw new IllegalArgumentException("invalid dimension");
            if (!region.has("mode") || !region.get("mode").isJsonPrimitive()) {
                throw new IllegalArgumentException("enabled region has no mode");
            }
            String mode = region.get("mode").getAsString();
            Map<ResourceLocation, List<Fence>> target;
            if (PLACE_BLOCK.equals(mode)) {
                target = place;
            } else if (FREEZE_UPDATES.equals(mode)) {
                target = freeze;
            } else {
                throw new IllegalArgumentException("unknown mode: " + mode);
            }

            if (!region.has("chunkFences") || !region.get("chunkFences").isJsonArray()) {
                throw new IllegalArgumentException("chunkFences must be an array");
            }
            List<Fence> fences = target.computeIfAbsent(dimension, ignored -> new ArrayList<>());
            for (JsonElement fenceElement : region.getAsJsonArray("chunkFences")) {
                if (!fenceElement.isJsonArray()) throw new IllegalArgumentException("fence must be an array");
                JsonArray values = fenceElement.getAsJsonArray();
                if (values.size() != 4) {
                    throw new IllegalArgumentException("fence must contain four integers");
                }
                long minX = integerValue(values.get(0));
                long minZ = integerValue(values.get(1));
                long maxX = integerValue(values.get(2));
                long maxZ = integerValue(values.get(3));
                if (minX > maxX || minZ > maxZ
                        || minX < MIN_CHUNK || minZ < MIN_CHUNK
                        || maxX > MAX_CHUNK || maxZ > MAX_CHUNK) {
                    throw new IllegalArgumentException("fence coordinates are out of range or inverted");
                }
                if (++fenceCount > MAX_FENCES) {
                    throw new IllegalArgumentException("too many fences");
                }
                fences.add(new Fence((int) minX, (int) minZ, (int) maxX, (int) maxZ));
            }
        }
        return new Snapshot(buildIndexes(place), buildIndexes(freeze));
    }

    private static long integerValue(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("fence coordinates must be integers");
        }
        try {
            return value.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("fence coordinates must be integers in range", e);
        }
    }

    private static Map<ResourceLocation, ChunkFenceIndex> buildIndexes(Map<ResourceLocation, List<Fence>> source) {
        Map<ResourceLocation, ChunkFenceIndex> result = new HashMap<>();
        for (var entry : source.entrySet()) {
            result.put(entry.getKey(), new ChunkFenceIndex(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    /** Queries mode B without disk I/O, JSON parsing, or area-sized allocation. */
    public boolean isFrozen(ResourceLocation dimension, int chunkX, int chunkZ) {
        ChunkFenceIndex index = snapshot.freeze().get(dimension);
        return index != null && index.contains(chunkX, chunkZ);
    }

    /** Queries mode A without disk I/O, JSON parsing, or area-sized allocation. */
    public boolean isPlaceBlocked(ResourceLocation dimension, int chunkX, int chunkZ) {
        ChunkFenceIndex index = snapshot.place().get(dimension);
        return index != null && index.contains(chunkX, chunkZ);
    }

    public int frozenFenceCount(ResourceLocation dimension) {
        ChunkFenceIndex index = snapshot.freeze().get(dimension);
        return index == null ? 0 : index.fenceCount();
    }

    public record Fence(int minX, int minZ, int maxX, int maxZ) {}

    private record Snapshot(Map<ResourceLocation, ChunkFenceIndex> place,
                            Map<ResourceLocation, ChunkFenceIndex> freeze) {
        private static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());

        private int placeFenceCount() {
            return place.values().stream().mapToInt(ChunkFenceIndex::fenceCount).sum();
        }

        private int freezeFenceCount() {
            return freeze.values().stream().mapToInt(ChunkFenceIndex::fenceCount).sum();
        }
    }

    /**
     * Fixed-size spatial buckets. Large rectangles stay in a small fallback
     * list; they are never expanded into one entry per protected chunk.
     */
    private static final class ChunkFenceIndex {
        private static final int BUCKET_SHIFT = 8;
        private static final int MAX_BUCKET_REFERENCES = 4096;
        private static final int MAX_INDEX_BUCKETS = 1_000_000;
        private static final int MAX_CANDIDATES_PER_BUCKET = 1024;
        private static final int MAX_BROAD_FENCES = 64;

        private final BucketMap buckets;
        private final Fence[] broad;
        private final int fenceCount;

        private ChunkFenceIndex(List<Fence> fences) {
            this.fenceCount = fences.size();
            Map<Long, List<Fence>> bucketLists = new HashMap<>();
            List<Fence> broadFences = new ArrayList<>();
            for (Fence fence : fences) {
                long minBucketX = fence.minX() >> BUCKET_SHIFT;
                long maxBucketX = fence.maxX() >> BUCKET_SHIFT;
                long minBucketZ = fence.minZ() >> BUCKET_SHIFT;
                long maxBucketZ = fence.maxZ() >> BUCKET_SHIFT;
                long bucketCount = (maxBucketX - minBucketX + 1) * (maxBucketZ - minBucketZ + 1);
                if (bucketCount > MAX_BUCKET_REFERENCES) {
                    broadFences.add(fence);
                    continue;
                }
                for (long bx = minBucketX; bx <= maxBucketX; bx++) {
                    for (long bz = minBucketZ; bz <= maxBucketZ; bz++) {
                        long key = pack((int) bx, (int) bz);
                        bucketLists.computeIfAbsent(key, ignored -> new ArrayList<>()).add(fence);
                        if (bucketLists.size() > MAX_INDEX_BUCKETS) {
                            throw new IllegalArgumentException("too many spatial buckets");
                        }
                    }
                }
            }
            Map<Long, Fence[]> compactBuckets = new HashMap<>(bucketLists.size());
            for (var entry : bucketLists.entrySet()) {
                if (entry.getValue().size() > MAX_CANDIDATES_PER_BUCKET) {
                    throw new IllegalArgumentException("too many overlapping fences in one spatial bucket");
                }
                compactBuckets.put(entry.getKey(), entry.getValue().toArray(Fence[]::new));
            }
            if (broadFences.size() > MAX_BROAD_FENCES) {
                throw new IllegalArgumentException("too many world-spanning fences");
            }
            this.buckets = new BucketMap(compactBuckets);
            this.broad = broadFences.toArray(Fence[]::new);
        }

        private boolean contains(int chunkX, int chunkZ) {
            Fence[] candidates = buckets.get(pack(chunkX >> BUCKET_SHIFT, chunkZ >> BUCKET_SHIFT));
            return contains(candidates, chunkX, chunkZ) || contains(broad, chunkX, chunkZ);
        }

        private static boolean contains(Fence[] fences, int chunkX, int chunkZ) {
            if (fences == null) return false;
            for (Fence fence : fences) {
                if (chunkX >= fence.minX() && chunkX <= fence.maxX()
                        && chunkZ >= fence.minZ() && chunkZ <= fence.maxZ()) return true;
            }
            return false;
        }

        private static int hash(long key) {
            key ^= key >>> 33;
            key *= 0xff51afd7ed558ccdL;
            key ^= key >>> 33;
            return (int) key;
        }

        private static long pack(int x, int z) {
            return ((long) x << 32) ^ (z & 0xffffffffL);
        }

        private int fenceCount() {
            return fenceCount;
        }

        /** Immutable open-addressed long-key map; query performs no boxing or allocation. */
        private static final class BucketMap {
            private final long[] keys;
            private final Fence[][] values;
            private final int mask;

            private BucketMap(Map<Long, Fence[]> source) {
                int capacity = 1;
                while (capacity < Math.max(2, source.size() * 2)) capacity <<= 1;
                keys = new long[capacity];
                values = new Fence[capacity][];
                mask = capacity - 1;
                for (var entry : source.entrySet()) {
                    long key = entry.getKey();
                    int slot = hash(key) & mask;
                    while (values[slot] != null) slot = (slot + 1) & mask;
                    keys[slot] = key;
                    values[slot] = entry.getValue();
                }
            }

            private Fence[] get(long key) {
                int slot = hash(key) & mask;
                while (values[slot] != null) {
                    if (keys[slot] == key) return values[slot];
                    slot = (slot + 1) & mask;
                }
                return null;
            }
        }
    }
}
