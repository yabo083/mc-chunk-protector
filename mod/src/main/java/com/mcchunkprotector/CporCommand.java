package com.mcchunkprotector;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;

public final class CporCommand {
    private CporCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("cpor").requires(source -> source.hasPermission(2));
        root.then(Commands.literal("status")
                .executes(context -> status(context.getSource(), currentX(context.getSource()), currentZ(context.getSource())))
                .then(Commands.argument("chunkX", chunkArgument())
                        .then(Commands.argument("chunkZ", chunkArgument())
                                .executes(context -> status(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "chunkX"),
                                        IntegerArgumentType.getInteger(context, "chunkZ"))))));
        root.then(Commands.literal("reload").executes(context -> reload(context.getSource())));
        root.then(editBranch("add", true));
        root.then(editBranch("remove", false));
        dispatcher.register(root);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> editBranch(
            String name, boolean adding) {
        return Commands.literal(name)
                .then(modeBranch("place", FrozenRegionManager.PLACE_BLOCK, adding))
                .then(modeBranch("freeze", FrozenRegionManager.FREEZE_UPDATES, adding));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> modeBranch(
            String name, String mode, boolean adding) {
        return Commands.literal(name)
                .then(Commands.literal("here").executes(context -> edit(context.getSource(), mode, adding,
                        currentX(context.getSource()), currentZ(context.getSource()),
                        currentX(context.getSource()), currentZ(context.getSource()))))
                .then(Commands.literal("rect")
                        .then(Commands.argument("x1", chunkArgument())
                                .then(Commands.argument("z1", chunkArgument())
                                        .then(Commands.argument("x2", chunkArgument())
                                                .then(Commands.argument("z2", chunkArgument())
                                                        .executes(context -> edit(context.getSource(), mode, adding,
                                                                IntegerArgumentType.getInteger(context, "x1"),
                                                                IntegerArgumentType.getInteger(context, "z1"),
                                                                IntegerArgumentType.getInteger(context, "x2"),
                                                                IntegerArgumentType.getInteger(context, "z2"))))))));
    }

    private static IntegerArgumentType chunkArgument() {
        return IntegerArgumentType.integer(FrozenRegionManager.MIN_CHUNK, FrozenRegionManager.MAX_CHUNK);
    }

    private static int edit(CommandSourceStack source, String mode, boolean adding,
                            int x1, int z1, int x2, int z2) {
        FrozenRegionManager manager = FrozenRegionManager.get();
        if (manager == null) return fail(source, "管理器尚未初始化");
        var area = new FrozenRegionManager.Fence(
                Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2));
        try {
            var editor = new RegionConfigEditor(manager);
            var result = adding
                    ? editor.add(source.getLevel().dimension().location(), mode, area)
                    : editor.remove(source.getLevel().dimension().location(), mode, area);
            String action = adding ? "添加" : "移除";
            source.sendSuccess(() -> Component.literal("[CPOR] " + action + (result.changed() ? "成功" : "无变化")
                    + "，当前矩形数=" + result.fenceCount()), true);
            return result.changed() ? 1 : 0;
        } catch (IOException | RuntimeException e) {
            ChunkProtectorMod.LOG.warn("[ChunkProtector] command edit failed", e);
            return fail(source, "修改失败：" + safeMessage(e));
        }
    }

    private static int status(CommandSourceStack source, int chunkX, int chunkZ) {
        FrozenRegionManager manager = FrozenRegionManager.get();
        if (manager == null) return fail(source, "管理器尚未初始化");
        var dimension = source.getLevel().dimension().location();
        boolean place = manager.isPlaceBlocked(dimension, chunkX, chunkZ);
        boolean freeze = manager.isFrozen(dimension, chunkX, chunkZ);
        int placeFences = manager.fenceCount(dimension, FrozenRegionManager.PLACE_BLOCK);
        int freezeFences = manager.fenceCount(dimension, FrozenRegionManager.FREEZE_UPDATES);
        source.sendSuccess(() -> Component.literal("[CPOR] " + dimension + " chunk(" + chunkX + "," + chunkZ
                + ") place=" + place + " freeze=" + freeze
                + "；矩形 place=" + placeFences + " freeze=" + freezeFences), false);
        return 1;
    }

    private static int reload(CommandSourceStack source) {
        FrozenRegionManager manager = FrozenRegionManager.get();
        if (manager == null) return fail(source, "管理器尚未初始化");
        try {
            manager.reload();
            source.sendSuccess(() -> Component.literal("[CPOR] 配置已重新加载"), true);
            return 1;
        } catch (IOException | RuntimeException e) {
            ChunkProtectorMod.LOG.warn("[ChunkProtector] command reload failed", e);
            return fail(source, "重载失败，继续使用旧配置：" + safeMessage(e));
        }
    }

    private static int currentX(CommandSourceStack source) {
        return FrozenRegionManager.chunkOf((int) Math.floor(source.getPosition().x));
    }

    private static int currentZ(CommandSourceStack source) {
        return FrozenRegionManager.chunkOf((int) Math.floor(source.getPosition().z));
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal("[CPOR] " + message));
        return 0;
    }

    private static String safeMessage(Exception error) {
        if (error instanceof IllegalArgumentException) {
            String message = error.getMessage();
            if (message != null && !message.isBlank()) return message;
        }
        return "操作失败，请查看服务器日志";
    }
}
