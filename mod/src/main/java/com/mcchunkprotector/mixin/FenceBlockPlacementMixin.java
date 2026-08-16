package com.mcchunkprotector.mixin;

import com.mcchunkprotector.ChunkProtectorMod;
import com.mcchunkprotector.FrozenRegionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 模式 B 冻结增强：冻结区内放置栅栏时，禁止其与邻居 "连接"（连接方向强制 false）。
 * 栅栏连接是在 getStateForPlacement（放置瞬间）建立的，不走 updateNeighborsAt，
 * 因此需要在此直接短路连接计算。
 */
@Mixin(FenceBlock.class)
public class FenceBlockPlacementMixin {

    @Inject(method = "getStateForPlacement(Lnet/minecraft/world/item/context/BlockPlaceContext;)"
            + "Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"), cancellable = true)
    private void mcchunkprotector$freezeFencePlacement(BlockPlaceContext ctx,
                                                        CallbackInfoReturnable<BlockState> cir) {
        var mgr = FrozenRegionManager.get();
        if (mgr == null) return;
        LevelAccessor level = ctx.getLevel();
        if (!(level instanceof net.minecraft.world.level.Level lv)) return;
        BlockPos pos = ctx.getClickedPos();
        String dim = lv.dimension().location().toString();
        long cx = FrozenRegionManager.chunkOf(pos.getX());
        long cz = FrozenRegionManager.chunkOf(pos.getZ());
        if (mgr.isFrozen(dim, cx, cz)) {
            // 返回 "无连接" 的默认状态：放置的栅栏不连任何邻居
            FenceBlock self = (FenceBlock) (Object) this;
            var neutral = self.defaultBlockState()
                    .setValue(FenceBlock.NORTH, false)
                    .setValue(FenceBlock.EAST, false)
                    .setValue(FenceBlock.SOUTH, false)
                    .setValue(FenceBlock.WEST, false);
            ChunkProtectorMod.LOG.info("[ChunkProtector][freeze] FenceBlock placement neutralized at ({},{},{}) chunk({},{})",
                    pos.getX(), pos.getY(), pos.getZ(), cx, cz);
            cir.setReturnValue(neutral);
        }
    }
}
