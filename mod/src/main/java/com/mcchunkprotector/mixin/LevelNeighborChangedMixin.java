package com.mcchunkprotector.mixin;

import com.mcchunkprotector.FrozenRegionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 冻结方块更新（模式 B）：拦截 Level.neighborChanged（5 参版本，服务端为主）。
 * pos = 当前被更新/刷新的邻居方块（目标），neighborPos = 更新来源。
 * 若 pos 的区块在冻结区，则该方块不响应邻居更新（连接/形状不刷新）。
 * 与 updateNeighborsAt(拦"出") 配合，形成"区内不响应外部/内部更新"的冻结。
 */
@Mixin(Level.class)
public class LevelNeighborChangedMixin {

    @Inject(method = "neighborChanged(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;"
            + "Lnet/minecraft/core/BlockPos;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void mcchunkprotector$freezeNeighborChanged(
            BlockState state, BlockPos pos, Block block, BlockPos neighborPos, boolean movedByPiston,
            CallbackInfo ci) {
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
