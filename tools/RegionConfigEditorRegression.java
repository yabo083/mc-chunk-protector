package com.mcchunkprotector;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Random;

public final class RegionConfigEditorRegression {
    public static void main(String[] args) throws Exception {
        geometryRegression();
        randomizedGeometryRegression();
        largeSparseRegression();
        fragmentedGeometryRegression();
        alternatingMergeRegression();
        persistenceRegression();
        concurrentEditRegression();
        System.out.println("PASS: region editor union, subtraction, and persistence");
    }

    private static void randomizedGeometryRegression() {
        Random random = new Random(0xC0FFEE);
        for (int round = 0; round < 500; round++) {
            int ax1 = random.nextInt(13) - 6;
            int az1 = random.nextInt(13) - 6;
            int ax2 = ax1 + random.nextInt(5);
            int az2 = az1 + random.nextInt(5);
            int bx1 = random.nextInt(13) - 6;
            int bz1 = random.nextInt(13) - 6;
            int bx2 = bx1 + random.nextInt(5);
            int bz2 = bz1 + random.nextInt(5);
            var source = List.of(new FrozenRegionManager.Fence(ax1, az1, ax2, az2));
            var edit = new FrozenRegionManager.Fence(bx1, bz1, bx2, bz2);
            var added = RegionConfigEditor.add(source, edit);
            var removed = RegionConfigEditor.remove(source, edit);
            for (int x = -7; x <= 7; x++) {
                for (int z = -7; z <= 7; z++) {
                    boolean inSource = x >= ax1 && x <= ax2 && z >= az1 && z <= az2;
                    boolean inEdit = x >= bx1 && x <= bx2 && z >= bz1 && z <= bz2;
                    require(contains(added, x, z) == (inSource || inEdit), "random union mismatch");
                    require(contains(removed, x, z) == (inSource && !inEdit), "random removal mismatch");
                }
            }
        }
    }

    private static void largeSparseRegression() {
        var fences = new java.util.ArrayList<FrozenRegionManager.Fence>();
        for (int i = 0; i < 4_000; i++) fences.add(new FrozenRegionManager.Fence(i * 2, 0, i * 2, 0));
        long started = System.nanoTime();
        var result = RegionConfigEditor.add(fences, new FrozenRegionManager.Fence(-2, 0, -2, 0));
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        require(result.size() == 4_001, "sparse rectangles must remain distinct");
        require(elapsedMillis < 2_000, "large edit took too long: " + elapsedMillis + "ms");
    }

    private static void fragmentedGeometryRegression() {
        var fences = new java.util.ArrayList<FrozenRegionManager.Fence>();
        for (int i = 0; i < 2_000; i++) fences.add(new FrozenRegionManager.Fence(i * 2, 0, i * 2, 100));
        long started = System.nanoTime();
        try {
            RegionConfigEditor.add(fences, new FrozenRegionManager.Fence(0, 0, 4_000, 100));
            throw new AssertionError("fragmented edit must hit the work budget");
        } catch (IllegalArgumentException expected) {
            require((System.nanoTime() - started) / 1_000_000 < 2_000,
                    "fragmented edit rejection took too long");
        }
    }

    private static void alternatingMergeRegression() {
        var fences = new java.util.ArrayList<FrozenRegionManager.Fence>();
        fences.add(new FrozenRegionManager.Fence(0, 0, 0, 0));
        for (int i = 1; i <= 65; i++) {
            fences.add(new FrozenRegionManager.Fence(i, 0, i, i - 1));
            fences.add(new FrozenRegionManager.Fence(0, i, i, i));
        }
        long started = System.nanoTime();
        try {
            RegionConfigEditor.add(fences, new FrozenRegionManager.Fence(-2, -2, -2, -2));
            throw new AssertionError("alternating merge must hit the pass budget");
        } catch (IllegalArgumentException expected) {
            require((System.nanoTime() - started) / 1_000_000 < 2_000,
                    "alternating merge rejection took too long");
        }
    }

    private static void geometryRegression() {
        var joined = RegionConfigEditor.add(
                List.of(new FrozenRegionManager.Fence(0, 0, 1, 1)),
                new FrozenRegionManager.Fence(2, 0, 3, 1));
        require(joined.equals(List.of(new FrozenRegionManager.Fence(0, 0, 3, 1))),
                "adjacent rectangles must merge");

        var border = RegionConfigEditor.remove(
                List.of(new FrozenRegionManager.Fence(0, 0, 4, 4)),
                new FrozenRegionManager.Fence(1, 1, 3, 3));
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                boolean expected = x == 0 || x == 4 || z == 0 || z == 4;
                require(contains(border, x, z) == expected, "rectangle subtraction mismatch at " + x + "," + z);
            }
        }
    }

    private static void persistenceRegression() throws Exception {
        var dir = Files.createTempDirectory("cpor-editor-test");
        var config = dir.resolve("regions.json");
        Files.writeString(config, """
                {"version":1,"regions":[
                  {"id":"old-a","name":"old","enabled":true,"dimension":"minecraft:overworld","chunkFences":[[0,0,4,4]],"mode":"freeze-updates"},
                  {"id":"cpor:minecraft:overworld:freeze-updates","name":"disabled","enabled":false,"dimension":"minecraft:overworld","chunkFences":[[99,99,99,99]],"mode":"freeze-updates"}
                ]}
                """, StandardCharsets.UTF_8);

        FrozenRegionManager.init(config);
        var manager = FrozenRegionManager.get();
        var dimension = ResourceLocation.parse("minecraft:overworld");
        var editor = new RegionConfigEditor(manager);
        var result = editor.remove(dimension, FrozenRegionManager.FREEZE_UPDATES,
                new FrozenRegionManager.Fence(1, 1, 3, 3));

        require(result.changed(), "removal must report a change");
        require(result.fenceCount() == 4, "carved rectangle must produce four border rectangles");
        require(manager.isFrozen(dimension, 0, 0), "border must remain frozen");
        require(!manager.isFrozen(dimension, 2, 2), "carved center must be unfrozen immediately");

        JsonObject root = new Gson().fromJson(Files.readString(config), JsonObject.class);
        require(root.getAsJsonArray("regions").size() == 2, "matching enabled regions must be replaced, disabled preserved");
        require(root.getAsJsonArray("regions").get(1).getAsJsonObject().get("id").getAsString()
                        .startsWith("cpor:"),
                "canonical command-owned region must be written");
        require(root.getAsJsonArray("regions").get(1).getAsJsonObject().get("id").getAsString()
                        .endsWith(":2"),
                "disabled canonical id must be preserved without blocking a new command-owned region");

        Files.writeString(config, """
                {"version":1,"regions":[
                  {"id":"part-a","name":"a","enabled":true,"dimension":"minecraft:overworld","chunkFences":[[0,0,0,0]],"mode":"freeze-updates"},
                  {"id":"part-b","name":"b","enabled":true,"dimension":"minecraft:overworld","chunkFences":[[1,0,1,0]],"mode":"freeze-updates"}
                ]}
                """, StandardCharsets.UTF_8);
        manager.reload();
        var canonicalized = editor.add(dimension, FrozenRegionManager.FREEZE_UPDATES,
                new FrozenRegionManager.Fence(0, 0, 1, 0));
        require(canonicalized.changed(), "no-op geometry must still canonicalize matching records");
        root = new Gson().fromJson(Files.readString(config), JsonObject.class);
        require(root.getAsJsonArray("regions").size() == 1, "canonicalization must leave one managed record");
        require(root.getAsJsonArray("regions").get(0).getAsJsonObject().get("id").getAsString().startsWith("cpor:"),
                "canonicalized record must be command-owned");
    }

    private static void concurrentEditRegression() throws Exception {
        var dir = Files.createTempDirectory("cpor-concurrent-test");
        var config = dir.resolve("regions.json");
        String initial = "{\"version\":1,\"regions\":[]}";
        Files.writeString(config, initial, StandardCharsets.UTF_8);
        FrozenRegionManager.init(config);
        var manager = FrozenRegionManager.get();
        var document = manager.readConfigDocument();
        String external = "{\"version\":1,\"regions\":[]}\n";
        Files.writeString(config, external, StandardCharsets.UTF_8);
        try {
            manager.replaceConfig(document.root(), document.digest());
            throw new AssertionError("concurrent external edit must be rejected");
        } catch (IllegalStateException expected) {
            require(Files.readString(config).equals(external), "external edit must remain untouched");
        }
    }

    private static boolean contains(List<FrozenRegionManager.Fence> fences, int x, int z) {
        return fences.stream().anyMatch(fence -> x >= fence.minX() && x <= fence.maxX()
                && z >= fence.minZ() && z <= fence.maxZ());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
