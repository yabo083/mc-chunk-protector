package com.mcchunkprotector;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * 服务端专用 mod：按区块实现「防放置」与「冻结方块更新」。
 * 仅服务端加载（客户端侧不加载逻辑）。
 */
@Mod(ChunkProtectorMod.MODID)
public class ChunkProtectorMod {
    public static final String MODID = "mcchunkprotector";
    public static final Logger LOG = LoggerFactory.getLogger(MODID);

    public ChunkProtectorMod(IEventBus modBus) {
        // 注册游戏事件。此 mod 拦截服务端方块事件，在 PCL2 单机(集成服务器)、局域网、独立服务端均生效；
        // 逻辑本身只有服务器线程会触发，客户端侧无副作用。
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        CporCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerAboutToStartEvent event) {
        Path cfg = worldRoot(event.getServer())
                .resolve("serverconfig/mcchunkprotector/regions.json")
                .normalize();
        FrozenRegionManager.init(cfg);
        LOG.info("[ChunkProtector] init, config={}", cfg);
    }

    private static Path worldRoot(MinecraftServer server) {
        try {
            java.lang.reflect.Method method;
            try {
                method = MinecraftServer.class.getMethod("getWorldPath", LevelResource.class);
            } catch (NoSuchMethodException ignored) {
                // The production 1.21.1 classpath used by the direct javac build exposes this mapped name.
                method = MinecraftServer.class.getMethod("a", LevelResource.class);
            }
            return (Path) method.invoke(server, LevelResource.ROOT);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to resolve the active world directory", error);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        var mgr = FrozenRegionManager.get();
        if (mgr != null) mgr.tick();
    }

    /** 模式 A：防放置（EntityPlaceEvent，放置前可取消）。 */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        var mgr = FrozenRegionManager.get();
        if (mgr == null) return;
        var pos = event.getPos();
        int cx = FrozenRegionManager.chunkOf(pos.getX());
        int cz = FrozenRegionManager.chunkOf(pos.getZ());
        if (event.getLevel() instanceof Level lv) {
            ResourceLocation dim = lv.dimension().location();
            if (mgr.isPlaceBlocked(dim, cx, cz)) {
                event.setCanceled(true);
            }
        }
    }
}
