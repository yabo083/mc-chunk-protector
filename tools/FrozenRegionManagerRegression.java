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
            write(config, "{\"version\":1,\"regions\":[{\"enabled\":true,\"dimension\":\"minecraft:overworld\",\"mode\":\"freeze-updates\",\"chunkFences\":[[-2,-1,2,3],[100000,100000,100000,100000]]}]}");
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
}
