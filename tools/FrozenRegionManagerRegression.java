import com.mcchunkprotector.FrozenRegionManager;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FrozenRegionManagerRegression {
    private static final ResourceLocation OVERWORLD = ResourceLocation.tryParse("minecraft:overworld");

    public static void main(String[] args) throws Exception {
        Path config = Files.createTempFile("mcchunkprotector-regression-", ".json");
        try {
            write(config, "{\"version\":1,\"regions\":[{\"id\":\"base\",\"name\":\"base\",\"enabled\":true,\"dimension\":\"minecraft:overworld\",\"mode\":\"freeze-updates\",\"chunkFences\":[[-2,-1,2,3],[100000,100000,100000,100000]]}]}");
            FrozenRegionManager.init(config);
            var manager = FrozenRegionManager.get();

            check(manager.isFrozen(OVERWORLD, -2, -1), "inclusive lower boundary");
            check(manager.isFrozen(OVERWORLD, 2, 3), "inclusive upper boundary");
            check(!manager.isFrozen(OVERWORLD, -3, -1), "outside negative boundary");
            check(manager.isFrozen(OVERWORLD, 100000, 100000), "large rectangle");
            check(!manager.isFrozen(OVERWORLD, 99999, 100000), "large rectangle outside");
            check(FrozenRegionManager.chunkOf(-1) == -1, "negative block coordinate");
            check(FrozenRegionManager.chunkOf(-16) == -1, "negative chunk boundary");
            check(FrozenRegionManager.chunkOf(-17) == -2, "negative chunk floor");

            write(config, "{\"version\":1,\"regions\":[");
            manager.refresh();
            check(manager.isFrozen(OVERWORLD, 0, 0), "last valid snapshot after malformed write");
            check(manager.frozenFenceCount(OVERWORLD) == 2, "last valid fence count");

            write(config, "{\"version\":1,\"regions\":[{\"name\":\"invalid disabled\",\"enabled\":false,\"dimension\":\"minecraft:overworld\",\"mode\":\"freeze-updates\",\"chunkFences\":[]}]}");
            expectFailure(manager::reload, "disabled region must still be fully validated");
            check(manager.isFrozen(OVERWORLD, 0, 0), "invalid disabled record keeps last valid snapshot");

            write(config, "{\"version\":\"1\",\"regions\":[]}");
            expectFailure(manager::reload, "version must be a JSON integer");
            check(manager.isFrozen(OVERWORLD, 0, 0), "invalid version keeps last valid snapshot");

            write(config, "{\"version\":1,\"regions\":["
                    + "{\"id\":\"duplicate\",\"name\":\"a\",\"enabled\":false,\"dimension\":\"minecraft:overworld\",\"mode\":\"freeze-updates\",\"chunkFences\":[]},"
                    + "{\"id\":\"duplicate\",\"name\":\"b\",\"enabled\":true,\"dimension\":\"minecraft:overworld\",\"mode\":\"freeze-updates\",\"chunkFences\":[]}]}" );
            expectFailure(manager::reload, "region ids must be unique");
            check(manager.isFrozen(OVERWORLD, 0, 0), "duplicate ids keep last valid snapshot");

            StringBuilder broad = new StringBuilder("{\"version\":1,\"regions\":[");
            for (int i = 0; i < 65; i++) {
                if (i > 0) broad.append(',');
                broad.append("{\"id\":\"").append(i)
                        .append("\",\"name\":\"broad\",\"enabled\":true,\"dimension\":\"test:d")
                        .append(i)
                        .append("\",\"mode\":\"freeze-updates\",\"chunkFences\":[[-134217728,-134217728,134217727,134217727]]}");
            }
            write(config, broad.append("]}").toString());
            long started = System.nanoTime();
            expectFailure(manager::reload, "broad-fence budget must span all dimension indexes");
            check((System.nanoTime() - started) / 1_000_000 < 2_000, "global broad-fence rejection must be fast");

            StringBuilder overlapping = new StringBuilder("{\"version\":1,\"regions\":[");
            for (int i = 0; i < 1025; i++) {
                if (i > 0) overlapping.append(',');
                overlapping.append("{\"id\":\"").append(i).append("\",\"name\":\"overlap\",\"enabled\":true,\"dimension\":\"minecraft:overworld\",\"mode\":\"freeze-updates\",\"chunkFences\":[[0,0,0,0]]}");
            }
            write(config, overlapping.append("]}").toString());
            started = System.nanoTime();
            expectFailure(manager::reload, "overlapping bucket candidate limit");
            check((System.nanoTime() - started) / 1_000_000 < 2_000, "overlap rejection must be fast");

            Files.write(config, new byte[(16 * 1024 * 1024) + 1]);
            expectFailure(manager::reload, "bounded read must reject oversized config");
            System.out.println("PASS: FrozenRegionManager index and last-known-good snapshot");
        } finally {
            Files.deleteIfExists(config);
        }
    }

    private static void write(Path path, String content) throws Exception {
        Files.writeString(path, content, StandardCharsets.UTF_8);
        Thread.sleep(5);
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError("FAIL: " + name);
    }

    private static void expectFailure(ThrowingAction action, String name) throws Exception {
        try {
            action.run();
            throw new AssertionError("FAIL: " + name + " did not fail");
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
