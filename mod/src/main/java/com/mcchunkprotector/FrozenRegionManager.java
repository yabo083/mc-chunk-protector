package com.mcchunkprotector;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    static final int MAX_CONFIG_BYTES = 16 * 1024 * 1024;
    private static final int MAX_FENCES = 250_000;
    private static final Gson GSON = new Gson();
    public static final int MIN_CHUNK = Integer.MIN_VALUE >> 4;
    public static final int MAX_CHUNK = Integer.MAX_VALUE >> 4;

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
    public synchronized void refresh() {
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

            Snapshot parsed = parse(new String(readBounded(configPath), StandardCharsets.UTF_8));
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

    /** Forces a synchronous reload for the OP command. Invalid input leaves the current snapshot active. */
    public synchronized void reload() throws IOException {
        if (!Files.exists(configPath)) {
            snapshot = Snapshot.EMPTY;
            lastSize = -1L;
            lastModified = null;
            return;
        }
        BasicFileAttributes attrs = Files.readAttributes(configPath, BasicFileAttributes.class);
        if (attrs.size() > MAX_CONFIG_BYTES) {
            throw new IllegalArgumentException("config exceeds " + MAX_CONFIG_BYTES + " bytes");
        }
        Snapshot parsed = parse(new String(readBounded(configPath), StandardCharsets.UTF_8));
        install(parsed, attrs);
    }

    synchronized ConfigDocument readConfigDocument() throws IOException {
        if (!Files.exists(configPath)) {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            root.add("regions", new JsonArray());
            return new ConfigDocument(root, null);
        }
        BasicFileAttributes attrs = Files.readAttributes(configPath, BasicFileAttributes.class);
        if (attrs.size() > MAX_CONFIG_BYTES) {
            throw new IllegalArgumentException("config exceeds " + MAX_CONFIG_BYTES + " bytes");
        }
        byte[] bytes = readBounded(configPath);
        String raw = new String(bytes, StandardCharsets.UTF_8);
        ParsedConfig parsed = parseConfig(raw);
        return new ConfigDocument(parsed.root(), digest(bytes));
    }

    synchronized void replaceConfig(JsonObject root, byte[] expectedDigest) throws IOException {
        String raw = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root) + System.lineSeparator();
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CONFIG_BYTES) {
            throw new IllegalArgumentException("config exceeds " + MAX_CONFIG_BYTES + " bytes");
        }
        Snapshot parsed = buildSnapshot(parseConfig(root));

        Path parent = configPath.getParent();
        if (parent == null) throw new IOException("config path has no parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, configPath.getFileName().toString(), ".tmp");
        boolean committed = false;
        try {
            Files.write(temporary, bytes);
            BasicFileAttributes temporaryAttributes = Files.readAttributes(temporary, BasicFileAttributes.class);
            byte[] currentDigest = null;
            if (Files.exists(configPath)) {
                BasicFileAttributes current = Files.readAttributes(configPath, BasicFileAttributes.class);
                if (current.size() > MAX_CONFIG_BYTES) {
                    throw new IllegalArgumentException("config exceeds " + MAX_CONFIG_BYTES + " bytes");
                }
                currentDigest = digest(readBounded(configPath));
            }
            if (!Arrays.equals(expectedDigest, currentDigest)) {
                throw new IllegalStateException("regions.json changed during edit; retry the command");
            }
            Files.move(temporary, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            install(parsed, temporaryAttributes);
            committed = true;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                if (!committed) throw cleanupFailure;
                ChunkProtectorMod.LOG.warn("[ChunkProtector] unable to delete committed temp file {}", temporary,
                        cleanupFailure);
            }
        }
    }

    synchronized void ensureConfigUnchanged(byte[] expectedDigest) throws IOException {
        byte[] currentDigest = null;
        if (Files.exists(configPath)) {
            BasicFileAttributes current = Files.readAttributes(configPath, BasicFileAttributes.class);
            if (current.size() > MAX_CONFIG_BYTES) {
                throw new IllegalArgumentException("config exceeds " + MAX_CONFIG_BYTES + " bytes");
            }
            currentDigest = digest(readBounded(configPath));
        }
        if (!Arrays.equals(expectedDigest, currentDigest)) {
            throw new IllegalStateException("regions.json changed during edit; retry the command");
        }
    }

    private static byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static byte[] readBounded(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(MAX_CONFIG_BYTES + 1);
            if (bytes.length > MAX_CONFIG_BYTES) {
                throw new IllegalArgumentException("config exceeds " + MAX_CONFIG_BYTES + " bytes");
            }
            return bytes;
        }
    }

    private void install(Snapshot parsed, BasicFileAttributes attrs) {
        snapshot = parsed;
        lastSize = attrs.size();
        lastModified = attrs.lastModifiedTime();
        failedSize = -1L;
        failedModified = null;
        ticksUntilRefresh = REFRESH_INTERVAL_TICKS;
    }

    private void warnOnce(long size, FileTime modified, String reason) {
        if (size == failedSize && modified.equals(failedModified)) return;
        failedSize = size;
        failedModified = modified;
        ChunkProtectorMod.LOG.warn("[ChunkProtector] keeping last valid config: {}", reason);
    }

    private static Snapshot parse(String raw) {
        return buildSnapshot(parseConfig(raw));
    }

    private static ParsedConfig parseConfig(String raw) {
        return parseConfig(GSON.fromJson(raw, JsonObject.class));
    }

    private static ParsedConfig parseConfig(JsonObject root) {
        if (root == null || !root.has("version") || !root.get("version").isJsonPrimitive()
                || !root.get("version").getAsJsonPrimitive().isNumber()
                || integerValue(root.get("version")) != 1) {
            throw new IllegalArgumentException("regions.json must have version 1");
        }
        if (!root.has("regions") || !root.get("regions").isJsonArray()) {
            throw new IllegalArgumentException("regions.json regions must be an array");
        }

        Map<ResourceLocation, List<Fence>> place = new HashMap<>();
        Map<ResourceLocation, List<Fence>> freeze = new HashMap<>();
        Set<String> regionIds = new HashSet<>();
        int fenceCount = 0;
        for (JsonElement element : root.getAsJsonArray("regions")) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("region must be an object");
            JsonObject region = element.getAsJsonObject();
            String id = requiredString(region, "id");
            if (id.isEmpty()) throw new IllegalArgumentException("region id must not be empty");
            if (!regionIds.add(id)) throw new IllegalArgumentException("duplicate region id: " + id);
            if (requiredString(region, "name").isEmpty()) {
                throw new IllegalArgumentException("region name must not be empty");
            }
            if (!region.has("enabled") || !region.get("enabled").isJsonPrimitive()
                    || !region.get("enabled").getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("region enabled must be boolean");
            }
            boolean enabled = region.get("enabled").getAsBoolean();
            ResourceLocation dimension = ResourceLocation.tryParse(requiredString(region, "dimension"));
            if (dimension == null) throw new IllegalArgumentException("invalid dimension");
            String mode = requiredString(region, "mode");
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
            List<Fence> fences = enabled ? target.computeIfAbsent(dimension, ignored -> new ArrayList<>()) : null;
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
                if (enabled) fences.add(new Fence((int) minX, (int) minZ, (int) maxX, (int) maxZ));
            }
        }
        return new ParsedConfig(root, place, freeze);
    }

    private static Snapshot buildSnapshot(ParsedConfig parsed) {
        IndexBudget budget = new IndexBudget();
        return new Snapshot(buildIndexes(parsed.place(), budget), buildIndexes(parsed.freeze(), budget));
    }

    private static String requiredString(JsonObject object, String name) {
        if (!object.has(name) || !object.get(name).isJsonPrimitive()
                || !object.get(name).getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("region " + name + " must be a string");
        }
        return object.get(name).getAsString();
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

    private static Map<ResourceLocation, ChunkFenceIndex> buildIndexes(Map<ResourceLocation, List<Fence>> source,
                                                                        IndexBudget budget) {
        Map<ResourceLocation, ChunkFenceIndex> result = new HashMap<>();
        for (var entry : source.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                budget.addIndex();
                result.put(entry.getKey(), new ChunkFenceIndex(entry.getValue(), budget));
            }
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

    public int fenceCount(ResourceLocation dimension, String mode) {
        Map<ResourceLocation, ChunkFenceIndex> indexes = switch (mode) {
            case PLACE_BLOCK -> snapshot.place();
            case FREEZE_UPDATES -> snapshot.freeze();
            default -> throw new IllegalArgumentException("unknown mode: " + mode);
        };
        ChunkFenceIndex index = indexes.get(dimension);
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
        private static final int MAX_CANDIDATES_PER_BUCKET = 1024;

        private final BucketMap buckets;
        private final Fence[] broad;
        private final int fenceCount;

        private ChunkFenceIndex(List<Fence> fences, IndexBudget budget) {
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
                    budget.addBroadFence();
                    broadFences.add(fence);
                    continue;
                }
                for (long bx = minBucketX; bx <= maxBucketX; bx++) {
                    for (long bz = minBucketZ; bz <= maxBucketZ; bz++) {
                        long key = pack((int) bx, (int) bz);
                        List<Fence> candidates = bucketLists.get(key);
                        if (candidates == null) {
                            budget.addBucket();
                            candidates = new ArrayList<>();
                            bucketLists.put(key, candidates);
                        }
                        if (candidates.size() >= MAX_CANDIDATES_PER_BUCKET) {
                            throw new IllegalArgumentException("too many overlapping fences in one spatial bucket");
                        }
                        budget.addReference();
                        candidates.add(fence);
                    }
                }
            }
            Map<Long, Fence[]> compactBuckets = new HashMap<>(bucketLists.size());
            for (var entry : bucketLists.entrySet()) {
                compactBuckets.put(entry.getKey(), entry.getValue().toArray(Fence[]::new));
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

    private static final class IndexBudget {
        private static final int MAX_INDEXES = 4_096;
        private static final int MAX_BUCKETS = 262_144;
        private static final int MAX_REFERENCES = 2_000_000;
        private static final int MAX_BROAD_FENCES = 64;

        private int indexes;
        private int buckets;
        private int references;
        private int broadFences;

        private void addIndex() {
            if (++indexes > MAX_INDEXES) throw new IllegalArgumentException("too many dimension indexes");
        }

        private void addBucket() {
            if (++buckets > MAX_BUCKETS) throw new IllegalArgumentException("too many spatial buckets");
        }

        private void addReference() {
            if (++references > MAX_REFERENCES) {
                throw new IllegalArgumentException("too many spatial bucket references");
            }
        }

        private void addBroadFence() {
            if (++broadFences > MAX_BROAD_FENCES) {
                throw new IllegalArgumentException("too many world-spanning fences");
            }
        }
    }

    record ConfigDocument(JsonObject root, byte[] digest) {}

    private record ParsedConfig(JsonObject root, Map<ResourceLocation, List<Fence>> place,
                                Map<ResourceLocation, List<Fence>> freeze) {}
}
