package com.thebeyond.common.worldgen;

import com.thebeyond.TheBeyond;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Lab: /beyondorg break x y z breaks a block the way a player does, so the break hooks run on a server without one. */
@EventBusSubscriber(modid = TheBeyond.MODID)
public final class OrganicLabCommand {
    private OrganicLabCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        if (!OrganicExperiment.ON) return;
        e.getDispatcher().register(Commands.literal("beyondorg").requires(s -> s.hasPermission(2))
                .then(Commands.literal("break").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> {
                    ServerLevel level = c.getSource().getLevel();
                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                    FakePlayer fake = FakePlayerFactory.getMinecraft(level);
                    boolean ok = fake.gameMode.destroyBlock(pos);
                    c.getSource().sendSuccess(() -> Component.literal("beyondorg break " + pos.toShortString() + " " + ok), false);
                    return ok ? 1 : 0;
                }))));
    }
}
