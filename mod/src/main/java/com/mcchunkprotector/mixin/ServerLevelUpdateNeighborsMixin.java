package com.mcchunkprotector.mixin;

import com.mcchunkprotector.ChunkProtectorMod;
import com.mcchunkprotector.FrozenRegionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 冻结方块更新（模式 B）：ServerLevel 覆写的 updateNeighborsAt 入口，
 * 对冻结区块内的广播做短路（不向 6 个邻居发更新）。
 */
@Mixin(ServerLevel.class)
public class ServerLevelUpdateNeighborsMixin {
    @Inject(method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V",
            at = @At("HEAD"), cancellable = true)
    private void mcChunkProtector$freezeServerUpdate(BlockPos pos, Block block, CallbackInfo ci) {
        var mgr = FrozenRegionManager.get();
        if (mgr == null) return;
        var self = (ServerLevel) (Object) this;
        String dim = self.dimension().location().toString();
        long cx = FrozenRegionManager.chunkOf(pos.getX());
        long cz = FrozenRegionManager.chunkOf(pos.getZ());
        if (mgr.isFrozen(dim, cx, cz)) {
            ChunkProtectorMod.LOG.info("[ChunkProtector][freeze] ServerLevel.updateNeighborsAt short-circuited at block({},{}) dim={} chunk({},{})", pos.getX(), pos.getZ(), dim, cx, cz);
            ci.cancel();
        }
    }
}
