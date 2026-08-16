package com.mcchunkprotector;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 调试命令：
 *   /chunkprotector inspect <x> <y> <z>   —— 打印该方块 BlockState 到日志，用于验证冻结/连接状态。
 */
public final class InspectCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var projectNode = Commands.literal("chunkprotector");

        var inspectNode = Commands.literal("inspect")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> inspect(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "x"),
                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                IntegerArgumentType.getInteger(ctx, "z"))))));

        var simplaceNode = Commands.literal("simplace")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .then(Commands.argument("block", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(ctx -> simplace(
                                                        ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                        IntegerArgumentType.getInteger(ctx, "z"),
                                                        ctx))))));

        dispatcher.register(projectNode.then(inspectNode).then(simplaceNode));
    }

    /** 公开构造的 BlockPlaceContext（原构造器 protected，子类可调）。 */
    static final class OpenPlacementContext extends BlockPlaceContext {
        OpenPlacementContext(Level level, Player player, InteractionHand hand,
                             ItemStack stack, BlockHitResult hit) {
            super(level, player, hand, stack, hit);
        }
    }

    private static int simplace(CommandSourceStack src, int x, int y, int z,
                                CommandContext<CommandSourceStack> ctx) {
        String blockId = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "block");
        var rl = net.minecraft.resources.ResourceLocation.tryParse(blockId);
        var block = rl == null ? null
                : net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl);
        if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
            src.sendSuccess(() -> Component.literal("invalid block: " + blockId), false);
            return 0;
        }
        var level = src.getLevel();
        var pos = new net.minecraft.core.BlockPos(x, y, z);
        var state = block.defaultBlockState();
        var stack = new ItemStack(block.asItem());
        var bhr = new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        var placementCtx = new OpenPlacementContext(level, null, InteractionHand.MAIN_HAND, stack, bhr);
        long cx = FrozenRegionManager.chunkOf(x);
        long cz = FrozenRegionManager.chunkOf(z);
        String dim = level.dimension().location().toString();
        boolean frozen = FrozenRegionManager.get().isFrozen(dim, cx, cz);
        BlockState placed;
        try {
            placed = block.getStateForPlacement(placementCtx);
            if (placed == null) placed = state;
        } catch (Exception e) {
            placed = state;
        }
        ChunkProtectorMod.LOG.info("[ChunkProtector][simplace] {} at ({},{},{}) dim={} chunk({},{}) frozen={} raw={} placement={}",
                blockId, x, y, z, dim, cx, cz, frozen, state, placed);
        src.sendSuccess(() -> Component.literal("[ChunkProtector] simplace, see server log"), false);
        return 1;
    }

    private static int inspect(CommandSourceStack src, int x, int y, int z) {
        var level = src.getLevel();
        var pos = new BlockPos(x, y, z);
        var state = level.getBlockState(pos);
        var block = level.getBlockState(pos).getBlock();
        long cx = FrozenRegionManager.chunkOf(x);
        long cz = FrozenRegionManager.chunkOf(z);
        String dim = level.dimension().location().toString();
        boolean frozen = FrozenRegionManager.get().isFrozen(dim, cx, cz);
        ChunkProtectorMod.LOG.info("[ChunkProtector][inspect] block ({},{},{}) dim={} chunk({},{}) frozen={} state={}",
                x, y, z, dim, cx, cz, frozen, state);
        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "[ChunkProtector] inspected, see server log"), false);
        return 1;
    }
}
