package com.mcchunkprotector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RegionConfigEditor {
    // Commands execute on the server thread. Bound edit complexity independently of the runtime index limit.
    private static final int MAX_EDITED_FENCES = 8_192;
    private static final int MAX_INTERMEDIATE_FENCES = MAX_EDITED_FENCES * 4;
    private static final int MAX_GEOMETRY_OPERATIONS = 1_000_000;
    private static final int MAX_MERGE_PASSES = 64;

    private final FrozenRegionManager manager;

    RegionConfigEditor(FrozenRegionManager manager) {
        this.manager = manager;
    }

    EditResult add(ResourceLocation dimension, String mode, FrozenRegionManager.Fence area) throws IOException {
        return edit(dimension, mode, area, true);
    }

    EditResult remove(ResourceLocation dimension, String mode, FrozenRegionManager.Fence area) throws IOException {
        return edit(dimension, mode, area, false);
    }

    private EditResult edit(ResourceLocation dimension, String mode, FrozenRegionManager.Fence area,
                            boolean adding) throws IOException {
        validateMode(mode);
        FrozenRegionManager.ConfigDocument document = manager.readConfigDocument();
        JsonObject root = document.root();
        JsonArray source = root.getAsJsonArray("regions");
        JsonArray retained = new JsonArray();
        List<FrozenRegionManager.Fence> current = new ArrayList<>();
        List<JsonObject> matched = new ArrayList<>();
        Set<String> retainedIds = new HashSet<>();

        for (JsonElement element : source) {
            JsonObject region = element.getAsJsonObject();
            if (matches(region, dimension, mode)) {
                matched.add(region);
                for (JsonElement fence : region.getAsJsonArray("chunkFences")) {
                    JsonArray values = fence.getAsJsonArray();
                    current.add(new FrozenRegionManager.Fence(
                            values.get(0).getAsInt(), values.get(1).getAsInt(),
                            values.get(2).getAsInt(), values.get(3).getAsInt()));
                }
            } else {
                retained.add(element.deepCopy());
                retainedIds.add(region.get("id").getAsString());
            }
        }

        if (current.size() > MAX_EDITED_FENCES) {
            throw new IllegalArgumentException("mode has more than " + MAX_EDITED_FENCES + " editable rectangles");
        }
        List<FrozenRegionManager.Fence> before = merge(current);
        List<FrozenRegionManager.Fence> after = adding ? add(before, area) : remove(before, area);
        if (after.size() > MAX_EDITED_FENCES) {
            throw new IllegalArgumentException("edit would create more than " + MAX_EDITED_FENCES + " rectangles");
        }

        JsonObject canonical = after.isEmpty() ? null
                : regionJson(availableId(dimension, mode, retainedIds), dimension, mode, after);
        boolean alreadyCanonical = canonical == null ? matched.isEmpty()
                : matched.size() == 1 && canonical.equals(matched.getFirst());
        if (before.equals(after) && alreadyCanonical) {
            manager.ensureConfigUnchanged(document.digest());
            return new EditResult(false, after.size());
        }

        if (canonical != null) retained.add(canonical);
        root.add("regions", retained);
        manager.replaceConfig(root, document.digest());
        return new EditResult(true, after.size());
    }

    static List<FrozenRegionManager.Fence> add(List<FrozenRegionManager.Fence> source,
                                                FrozenRegionManager.Fence addition) {
        List<FrozenRegionManager.Fence> result = new ArrayList<>(merge(source));
        List<FrozenRegionManager.Fence> pending = new ArrayList<>();
        pending.add(addition);
        int operations = 0;
        for (FrozenRegionManager.Fence existing : result) {
            List<FrozenRegionManager.Fence> next = new ArrayList<>();
            for (FrozenRegionManager.Fence candidate : pending) {
                if (++operations > MAX_GEOMETRY_OPERATIONS) {
                    throw new IllegalArgumentException("edit geometry is too complex");
                }
                next.addAll(subtract(candidate, existing));
                if (next.size() > MAX_INTERMEDIATE_FENCES) {
                    throw new IllegalArgumentException("edit would create too many intermediate rectangles");
                }
            }
            pending = next;
            if (pending.isEmpty()) break;
        }
        result.addAll(pending);
        return merge(result);
    }

    static List<FrozenRegionManager.Fence> remove(List<FrozenRegionManager.Fence> source,
                                                   FrozenRegionManager.Fence removal) {
        List<FrozenRegionManager.Fence> result = new ArrayList<>();
        for (FrozenRegionManager.Fence fence : merge(source)) result.addAll(subtract(fence, removal));
        return merge(result);
    }

    private static List<FrozenRegionManager.Fence> subtract(FrozenRegionManager.Fence source,
                                                             FrozenRegionManager.Fence cut) {
        int minX = Math.max(source.minX(), cut.minX());
        int minZ = Math.max(source.minZ(), cut.minZ());
        int maxX = Math.min(source.maxX(), cut.maxX());
        int maxZ = Math.min(source.maxZ(), cut.maxZ());
        if (minX > maxX || minZ > maxZ) return List.of(source);

        List<FrozenRegionManager.Fence> result = new ArrayList<>(4);
        if (source.minZ() < minZ) result.add(new FrozenRegionManager.Fence(
                source.minX(), source.minZ(), source.maxX(), minZ - 1));
        if (maxZ < source.maxZ()) result.add(new FrozenRegionManager.Fence(
                source.minX(), maxZ + 1, source.maxX(), source.maxZ()));
        if (source.minX() < minX) result.add(new FrozenRegionManager.Fence(
                source.minX(), minZ, minX - 1, maxZ));
        if (maxX < source.maxX()) result.add(new FrozenRegionManager.Fence(
                maxX + 1, minZ, source.maxX(), maxZ));
        return result;
    }

    private static List<FrozenRegionManager.Fence> merge(List<FrozenRegionManager.Fence> source) {
        List<FrozenRegionManager.Fence> result = new ArrayList<>(source);
        int previousSize;
        int passes = 0;
        do {
            if (++passes > MAX_MERGE_PASSES) {
                throw new IllegalArgumentException("rectangle normalization is too complex");
            }
            previousSize = result.size();
            result = mergePass(result, Comparator.comparingInt(FrozenRegionManager.Fence::minZ)
                    .thenComparingInt(FrozenRegionManager.Fence::maxZ)
                    .thenComparingInt(FrozenRegionManager.Fence::minX), true);
            result = mergePass(result, Comparator.comparingInt(FrozenRegionManager.Fence::minX)
                    .thenComparingInt(FrozenRegionManager.Fence::maxX)
                    .thenComparingInt(FrozenRegionManager.Fence::minZ), false);
        } while (result.size() < previousSize);
        result.sort(Comparator.comparingInt(FrozenRegionManager.Fence::minX)
                .thenComparingInt(FrozenRegionManager.Fence::minZ)
                .thenComparingInt(FrozenRegionManager.Fence::maxX)
                .thenComparingInt(FrozenRegionManager.Fence::maxZ));
        return List.copyOf(result);
    }

    private static ArrayList<FrozenRegionManager.Fence> mergePass(List<FrozenRegionManager.Fence> source,
                                                                  Comparator<FrozenRegionManager.Fence> order,
                                                                  boolean horizontal) {
        ArrayList<FrozenRegionManager.Fence> sorted = new ArrayList<>(source);
        sorted.sort(order);
        ArrayList<FrozenRegionManager.Fence> result = new ArrayList<>(sorted.size());
        for (FrozenRegionManager.Fence next : sorted) {
            if (result.isEmpty()) {
                result.add(next);
                continue;
            }
            int lastIndex = result.size() - 1;
            FrozenRegionManager.Fence last = result.get(lastIndex);
            FrozenRegionManager.Fence merged = horizontal ? mergeHorizontal(last, next) : mergeVertical(last, next);
            if (merged == null) result.add(next);
            else result.set(lastIndex, merged);
        }
        return result;
    }

    private static FrozenRegionManager.Fence mergeHorizontal(FrozenRegionManager.Fence a,
                                                              FrozenRegionManager.Fence b) {
        if (a.minZ() != b.minZ() || a.maxZ() != b.maxZ() || (long) a.maxX() + 1 < b.minX()) return null;
        return new FrozenRegionManager.Fence(a.minX(), a.minZ(), Math.max(a.maxX(), b.maxX()), a.maxZ());
    }

    private static FrozenRegionManager.Fence mergeVertical(FrozenRegionManager.Fence a,
                                                            FrozenRegionManager.Fence b) {
        if (a.minX() != b.minX() || a.maxX() != b.maxX() || (long) a.maxZ() + 1 < b.minZ()) return null;
        return new FrozenRegionManager.Fence(a.minX(), a.minZ(), a.maxX(), Math.max(a.maxZ(), b.maxZ()));
    }

    private static boolean matches(JsonObject region, ResourceLocation dimension, String mode) {
        return region.get("enabled").getAsBoolean()
                && dimension.toString().equals(region.get("dimension").getAsString())
                && mode.equals(region.get("mode").getAsString());
    }

    private static String availableId(ResourceLocation dimension, String mode, Set<String> retainedIds) {
        String base = "cpor:" + dimension + ":" + mode;
        if (!retainedIds.contains(base)) return base;
        for (int suffix = 2; suffix <= retainedIds.size() + 1; suffix++) {
            String candidate = base + ":" + suffix;
            if (!retainedIds.contains(candidate)) return candidate;
        }
        throw new IllegalArgumentException("unable to allocate command region id");
    }

    private static JsonObject regionJson(String id, ResourceLocation dimension, String mode,
                                          List<FrozenRegionManager.Fence> fences) {
        JsonObject region = new JsonObject();
        region.addProperty("id", id);
        region.addProperty("name", "cpor " + mode + " " + dimension);
        region.addProperty("enabled", true);
        region.addProperty("dimension", dimension.toString());
        JsonArray values = new JsonArray();
        for (FrozenRegionManager.Fence fence : fences) {
            JsonArray item = new JsonArray();
            item.add(fence.minX());
            item.add(fence.minZ());
            item.add(fence.maxX());
            item.add(fence.maxZ());
            values.add(item);
        }
        region.add("chunkFences", values);
        region.addProperty("mode", mode);
        return region;
    }

    private static void validateMode(String mode) {
        if (!FrozenRegionManager.PLACE_BLOCK.equals(mode) && !FrozenRegionManager.FREEZE_UPDATES.equals(mode)) {
            throw new IllegalArgumentException("unknown mode: " + mode);
        }
    }

    record EditResult(boolean changed, int fenceCount) {}
}
