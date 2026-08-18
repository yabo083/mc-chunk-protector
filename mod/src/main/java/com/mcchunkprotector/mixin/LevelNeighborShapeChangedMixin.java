package com.mcchunkprotector.mixin;

import com.mcchunkprotector.FrozenRegionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents neighbor shape updates from rewriting block states inside frozen chunks. */
@Mixin(Level.class)
public class LevelNeighborShapeChangedMixin {

    @Inject(method = "neighborShapeChanged(Lnet/minecraft/core/Direction;"
            + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/core/BlockPos;II)V",
            at = @At("HEAD"), cancellable = true)
    private void mcchunkprotector$freezeNeighborShapeChanged(
            Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos,
            int flags, int maxUpdateDepth, CallbackInfo ci) {
        var mgr = FrozenRegionManager.get();
        if (mgr == null) return;

        var level = (Level) (Object) this;
        var dim = level.dimension().location();
        int cx = FrozenRegionManager.chunkOf(pos.getX());
        int cz = FrozenRegionManager.chunkOf(pos.getZ());
        if (mgr.isFrozen(dim, cx, cz)) {
            ci.cancel();
        }
    }
}
