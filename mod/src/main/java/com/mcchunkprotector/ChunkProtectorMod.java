package com.mcchunkprotector;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.world.level.Level;
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
    public void onServerStarting(ServerAboutToStartEvent event) {
        // 配置路径：<server>/kubejs/config/regions.json（与 GUI / 旧 KubeJS 脚本共契约）
        Path cfg = FMLPaths.GAMEDIR.get().resolve("kubejs/config/regions.json");
        FrozenRegionManager.init(cfg);
        LOG.info("[ChunkProtector] init, config={}", cfg);
    }

    /** 模式 A：防放置（EntityPlaceEvent，放置前可取消）。 */
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        var mgr = FrozenRegionManager.get();
        if (mgr == null) return;
        var pos = event.getPos();
        long cx = FrozenRegionManager.chunkOf(pos.getX());
        long cz = FrozenRegionManager.chunkOf(pos.getZ());
        if (event.getLevel() instanceof Level lv) {
            String dim = lv.dimension().location().toString();
            if (mgr.isPlaceBlocked(dim, cx, cz)) {
                event.setCanceled(true);
            }
        }
    }
}
