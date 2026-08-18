package com.mcchunkprotector.mixin;

import com.mcchunkprotector.FrozenRegionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 冻结方块更新（模式 B）：拦截 Level.updateNeighborsAt 的"向外广播"入口，
 * 若被更新方块的区块在冻结区，则短路，不向 6 个邻居广播更新。
 * 这样放置/改变方块后邻居不刷新（如栅栏放置后不自动连接）。
 */
@Mixin(Level.class)
public class LevelUpdateNeighborsMixin {
    @Inject(method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;)V",
            at = @At("HEAD"), cancellable = true)
    private void mcChunkProtector$freezeUpdateNeighborsAt(BlockPos pos, Block block, CallbackInfo ci) {
        var mgr = FrozenRegionManager.get();
        if (mgr == null) return;
        var self = (Level) (Object) this;
        var dim = self.dimension().location();
        int cx = FrozenRegionManager.chunkOf(pos.getX());
        int cz = FrozenRegionManager.chunkOf(pos.getZ());
        if (mgr.isFrozen(dim, cx, cz)) {
            ci.cancel();
        }
    }
}
